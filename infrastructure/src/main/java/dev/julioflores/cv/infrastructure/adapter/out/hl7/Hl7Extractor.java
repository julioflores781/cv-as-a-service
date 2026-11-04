package dev.julioflores.cv.infrastructure.adapter.out.hl7;

import ca.uhn.hl7v2.model.Composite;
import ca.uhn.hl7v2.model.Message;
import ca.uhn.hl7v2.model.Primitive;
import ca.uhn.hl7v2.model.Type;
import ca.uhn.hl7v2.model.v251.message.ADT_A01;
import ca.uhn.hl7v2.model.v251.message.ORU_R01;
import ca.uhn.hl7v2.model.v251.segment.MSH;
import ca.uhn.hl7v2.model.v251.segment.OBX;
import ca.uhn.hl7v2.model.v251.segment.PID;
import ca.uhn.hl7v2.model.v251.segment.PV1;
import ca.uhn.hl7v2.HL7Exception;
import dev.julioflores.cv.application.service.InvalidHl7MessageException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

/** Lee un mensaje HL7 v2.5.1 ya interpretado por HAPI y extrae solo lo que usa la demo. */
final class Hl7Extractor {

    private Hl7Extractor() {}

    static ClinicalMessage extract(Message message) throws HL7Exception {
        if (message instanceof ADT_A01 adt) {
            return build(adt.getMSH(), adt.getPID(), adt.getPV1(), List.of());
        }
        if (message instanceof ORU_R01 oru) {
            var patientGroup = oru.getPATIENT_RESULT().getPATIENT();
            List<ClinicalMessage.Observation> observations = new ArrayList<>();
            for (var order : oru.getPATIENT_RESULT().getORDER_OBSERVATIONAll()) {
                for (var observation : order.getOBSERVATIONAll()) {
                    observations.add(observation(observation.getOBX()));
                }
            }
            return build(oru.getMSH(), patientGroup.getPID(), patientGroup.getVISIT().getPV1(), observations);
        }
        throw new InvalidHl7MessageException("the parsed message has an unexpected structure");
    }

    private static ClinicalMessage build(MSH msh, PID pid, PV1 pv1, List<ClinicalMessage.Observation> observations) {
        return new ClinicalMessage(
                text(msh.getMessageType().getMessageCode()) + "^" + text(msh.getMessageType().getTriggerEvent()),
                text(msh.getVersionID().getVersionID()),
                text(msh.getMessageControlID()),
                patient(pid),
                visit(pv1),
                observations);
    }

    private static ClinicalMessage.Patient patient(PID pid) {
        var id = pid.getPatientIdentifierList().length > 0 ? pid.getPatientIdentifierList(0) : null;
        var name = pid.getPatientName().length > 0 ? pid.getPatientName(0) : null;
        var patient = new ClinicalMessage.Patient(
                id == null ? null : text(id.getIDNumber()),
                id == null ? null : text(id.getAssigningAuthority().getNamespaceID()),
                name == null ? null : text(name.getFamilyName().getSurname()),
                name == null ? null : text(name.getGivenName()),
                birthDate(text(pid.getDateTimeOfBirth().getTime())),
                text(pid.getAdministrativeSex()));
        return isEmpty(patient) ? null : patient;
    }

    private static ClinicalMessage.Visit visit(PV1 pv1) {
        var location = pv1.getAssignedPatientLocation();
        String where = String.join(
                "/",
                Stream.of(
                                text(location.getPointOfCare()), text(location.getRoom()), text(location.getBed()))
                        .filter(Objects::nonNull)
                        .toList());
        var visit = new ClinicalMessage.Visit(text(pv1.getPatientClass()), where.isEmpty() ? null : where);
        return visit.patientClass() == null && visit.location() == null ? null : visit;
    }

    private static ClinicalMessage.Observation observation(OBX obx) throws HL7Exception {
        var identifier = obx.getObservationIdentifier();
        String unit = text(obx.getUnits().getIdentifier());
        BigDecimal numeric = null;
        String textValue = null;
        if (obx.getObservationValue().length > 0) {
            String raw = valueOf(obx.getObservationValue(0).getData());
            if ("NM".equals(text(obx.getValueType())) && raw != null) {
                numeric = number(raw);
            }
            textValue = numeric == null ? raw : null;
        }
        return new ClinicalMessage.Observation(
                text(identifier.getIdentifier()),
                text(identifier.getText()),
                text(identifier.getNameOfCodingSystem()),
                numeric,
                textValue,
                unit,
                text(obx.getReferencesRange()),
                text(obx.getObservationResultStatus()));
    }

    /** Valor de un campo variable: texto si es primitivo; para códigos (CE, CWE…) su texto, o su identificador. */
    private static String valueOf(Type data) {
        if (data instanceof Primitive primitive) {
            return blankToNull(primitive.getValue());
        }
        if (data instanceof Composite composite && composite.getComponents().length > 1) {
            String text = valueOf(composite.getComponents()[1]);
            return text != null ? text : valueOf(composite.getComponents()[0]);
        }
        return null;
    }

    private static BigDecimal number(String raw) {
        try {
            return new BigDecimal(raw.strip());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** HL7 envía AAAAMMDD (a veces con hora); FHIR quiere AAAA-MM-DD. Lo que no encaje se descarta. */
    private static String birthDate(String raw) {
        if (raw == null || raw.length() < 8 || !raw.substring(0, 8).chars().allMatch(Character::isDigit)) {
            return null;
        }
        String iso = raw.substring(0, 4) + "-" + raw.substring(4, 6) + "-" + raw.substring(6, 8);
        try {
            return LocalDate.parse(iso).toString();
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private static boolean isEmpty(ClinicalMessage.Patient p) {
        return p.id() == null && p.familyName() == null && p.givenName() == null
                && p.birthDate() == null && p.gender() == null;
    }

    private static String text(Primitive primitive) {
        return blankToNull(primitive.getValue());
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
