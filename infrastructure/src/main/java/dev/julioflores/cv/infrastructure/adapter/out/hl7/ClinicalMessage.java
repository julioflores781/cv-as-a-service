package dev.julioflores.cv.infrastructure.adapter.out.hl7;

import java.math.BigDecimal;
import java.util.List;

/**
 * Lo que la demo entiende de un mensaje HL7 v2. Es el modelo común del que salen la vista JSON
 * y el Bundle FHIR: así el mapeo desde HL7 se hace una sola vez.
 */
record ClinicalMessage(
        String messageType,
        String version,
        String controlId,
        Patient patient,
        Visit visit,
        List<Observation> observations) {

    record Patient(String id, String idAssigner, String familyName, String givenName, String birthDate, String gender) {}

    record Visit(String patientClass, String location) {}

    record Observation(
            String code,
            String name,
            String codingSystem,
            BigDecimal numericValue,
            String textValue,
            String unit,
            String referenceRange,
            String status) {}
}
