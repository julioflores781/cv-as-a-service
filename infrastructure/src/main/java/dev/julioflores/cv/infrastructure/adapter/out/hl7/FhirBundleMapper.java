package dev.julioflores.cv.infrastructure.adapter.out.hl7;

import java.math.BigDecimal;
import java.util.List;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.DateType;
import org.hl7.fhir.r4.model.Encounter;
import org.hl7.fhir.r4.model.Enumerations.AdministrativeGender;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Quantity;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.StringType;

/** Traduce el {@link ClinicalMessage} a un Bundle FHIR R4 de tipo {@code collection}. */
final class FhirBundleMapper {

    private static final String PATIENT_ID = "patient";
    private static final String ENCOUNTER_ID = "encounter";
    private static final String ACT_CODE = "http://terminology.hl7.org/CodeSystem/v3-ActCode";
    private static final String V2_PATIENT_CLASS = "http://terminology.hl7.org/CodeSystem/v2-0004";

    private FhirBundleMapper() {}

    static Bundle toBundle(ClinicalMessage message) {
        Bundle bundle = new Bundle();
        bundle.setType(Bundle.BundleType.COLLECTION);
        if (message.controlId() != null) {
            bundle.setId(message.controlId());
        }
        Reference subject = null;
        if (message.patient() != null) {
            bundle.addEntry().setResource(patient(message.patient()));
            subject = new Reference("Patient/" + PATIENT_ID);
        }
        if (message.visit() != null) {
            bundle.addEntry().setResource(encounter(message.visit(), subject));
        }
        List<ClinicalMessage.Observation> observations = message.observations();
        for (int i = 0; i < observations.size(); i++) {
            bundle.addEntry().setResource(observation(observations.get(i), i + 1, subject));
        }
        return bundle;
    }

    private static Patient patient(ClinicalMessage.Patient source) {
        Patient patient = new Patient();
        patient.setId(PATIENT_ID);
        if (source.id() != null) {
            patient.addIdentifier().setValue(source.id()).getAssigner().setDisplay(source.idAssigner());
        }
        if (source.familyName() != null || source.givenName() != null) {
            var name = patient.addName();
            name.setFamily(source.familyName());
            if (source.givenName() != null) {
                name.addGiven(source.givenName());
            }
        }
        if (source.birthDate() != null) {
            patient.setBirthDateElement(new DateType(source.birthDate()));
        }
        patient.setGender(gender(source.gender()));
        return patient;
    }

    private static AdministrativeGender gender(String code) {
        if (code == null) {
            return AdministrativeGender.UNKNOWN;
        }
        return switch (code) {
            case "M" -> AdministrativeGender.MALE;
            case "F" -> AdministrativeGender.FEMALE;
            case "O", "A", "N" -> AdministrativeGender.OTHER;
            default -> AdministrativeGender.UNKNOWN;
        };
    }

    private static Encounter encounter(ClinicalMessage.Visit source, Reference subject) {
        Encounter encounter = new Encounter();
        encounter.setId(ENCOUNTER_ID);
        encounter.setStatus(Encounter.EncounterStatus.INPROGRESS);
        encounter.setClass_(patientClass(source.patientClass()));
        encounter.setSubject(subject);
        if (source.location() != null) {
            encounter.addLocation().setLocation(new Reference().setDisplay(source.location()));
        }
        return encounter;
    }

    /** I/O/E tienen equivalente en ActCode; cualquier otra clase se conserva con el código HL7 original. */
    private static Coding patientClass(String code) {
        return switch (code == null ? "" : code) {
            case "I" -> new Coding(ACT_CODE, "IMP", "inpatient encounter");
            case "E" -> new Coding(ACT_CODE, "EMER", "emergency");
            case "", "O" -> new Coding(ACT_CODE, "AMB", "ambulatory");
            default -> new Coding(V2_PATIENT_CLASS, code, null);
        };
    }

    private static Observation observation(ClinicalMessage.Observation source, int number, Reference subject) {
        Observation observation = new Observation();
        observation.setId("observation-" + number);
        observation.setStatus(status(source.status()));
        CodeableConcept code = observation.getCode();
        Coding coding = code.addCoding().setCode(source.code()).setDisplay(source.name());
        if ("LN".equals(source.codingSystem())) {
            coding.setSystem("http://loinc.org");
        }
        code.setText(source.name());
        observation.setSubject(subject);
        BigDecimal numeric = source.numericValue();
        if (numeric != null) {
            observation.setValue(new Quantity().setValue(numeric).setUnit(source.unit()));
        } else if (source.textValue() != null) {
            observation.setValue(new StringType(source.textValue()));
        }
        if (source.referenceRange() != null) {
            observation.addReferenceRange().setText(source.referenceRange());
        }
        return observation;
    }

    private static Observation.ObservationStatus status(String code) {
        if (code == null) {
            return Observation.ObservationStatus.UNKNOWN;
        }
        return switch (code) {
            case "F" -> Observation.ObservationStatus.FINAL;
            case "P", "I" -> Observation.ObservationStatus.PRELIMINARY;
            case "C" -> Observation.ObservationStatus.CORRECTED;
            case "X" -> Observation.ObservationStatus.CANCELLED;
            default -> Observation.ObservationStatus.UNKNOWN;
        };
    }
}
