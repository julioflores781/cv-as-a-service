package dev.julioflores.cv.infrastructure.adapter.in.rest;

/** Mensajes HL7 sintéticos (pacientes inventados) y sus traducciones, para Swagger. */
final class Hl7Examples {

    private Hl7Examples() {}

    static final String ADT_A01 = """
            MSH|^~\\&|HIS|HOSPITAL|LAB|HOSPITAL|20260101120000||ADT^A01^ADT_A01|MSG00001|P|2.5.1
            EVN|A01|20260101120000
            PID|1||12345^^^HOSP^MR||Pérez^Juan||19800101|M
            PV1|1|I|UCI^1^A""";

    static final String ORU_R01 = """
            MSH|^~\\&|LAB|HOSPITAL|HIS|HOSPITAL|20260101130000||ORU^R01^ORU_R01|MSG00002|P|2.5.1
            PID|1||12345^^^HOSP^MR||Pérez^Juan||19800101|M
            OBR|1|||718-7^Hemoglobina^LN
            OBX|1|NM|718-7^Hemoglobina^LN||13.5|g/dL|12.0-16.0|N|||F
            OBX|2|ST|11502-2^Comentario^LN||Muestra sin hemólisis||||||F""";

    static final String ADT_A01_JSON = """
            {
              "messageType" : "ADT^A01",
              "version" : "2.5.1",
              "controlId" : "MSG00001",
              "patient" : {
                "id" : "12345",
                "idAssigner" : "HOSP",
                "familyName" : "Pérez",
                "givenName" : "Juan",
                "birthDate" : "1980-01-01",
                "gender" : "M"
              },
              "visit" : {
                "patientClass" : "I",
                "location" : "UCI/1/A"
              }
            }""";

    static final String ADT_A01_FHIR = """
            {
              "resourceType": "Bundle",
              "id": "MSG00001",
              "type": "collection",
              "entry": [ {
                "resource": {
                  "resourceType": "Patient",
                  "id": "patient",
                  "identifier": [ {
                    "value": "12345",
                    "assigner": {
                      "display": "HOSP"
                    }
                  } ],
                  "name": [ {
                    "family": "Pérez",
                    "given": [ "Juan" ]
                  } ],
                  "gender": "male",
                  "birthDate": "1980-01-01"
                }
              }, {
                "resource": {
                  "resourceType": "Encounter",
                  "id": "encounter",
                  "status": "in-progress",
                  "class": {
                    "system": "http://terminology.hl7.org/CodeSystem/v3-ActCode",
                    "code": "IMP",
                    "display": "inpatient encounter"
                  },
                  "subject": {
                    "reference": "Patient/patient"
                  },
                  "location": [ {
                    "location": {
                      "display": "UCI/1/A"
                    }
                  } ]
                }
              } ]
            }""";

    static final String ORU_R01_JSON = """
            {
              "messageType" : "ORU^R01",
              "version" : "2.5.1",
              "controlId" : "MSG00002",
              "patient" : {
                "id" : "12345",
                "idAssigner" : "HOSP",
                "familyName" : "Pérez",
                "givenName" : "Juan",
                "birthDate" : "1980-01-01",
                "gender" : "M"
              },
              "observations" : [ {
                "code" : "718-7",
                "name" : "Hemoglobina",
                "codingSystem" : "LN",
                "numericValue" : 13.5,
                "unit" : "g/dL",
                "referenceRange" : "12.0-16.0",
                "status" : "F"
              }, {
                "code" : "11502-2",
                "name" : "Comentario",
                "codingSystem" : "LN",
                "textValue" : "Muestra sin hemólisis",
                "status" : "F"
              } ]
            }""";

    static final String ORU_R01_FHIR = """
            {
              "resourceType": "Bundle",
              "id": "MSG00002",
              "type": "collection",
              "entry": [ {
                "resource": {
                  "resourceType": "Patient",
                  "id": "patient",
                  "identifier": [ {
                    "value": "12345",
                    "assigner": {
                      "display": "HOSP"
                    }
                  } ],
                  "name": [ {
                    "family": "Pérez",
                    "given": [ "Juan" ]
                  } ],
                  "gender": "male",
                  "birthDate": "1980-01-01"
                }
              }, {
                "resource": {
                  "resourceType": "Observation",
                  "id": "observation-1",
                  "status": "final",
                  "code": {
                    "coding": [ {
                      "system": "http://loinc.org",
                      "code": "718-7",
                      "display": "Hemoglobina"
                    } ],
                    "text": "Hemoglobina"
                  },
                  "subject": {
                    "reference": "Patient/patient"
                  },
                  "valueQuantity": {
                    "value": 13.5,
                    "unit": "g/dL"
                  },
                  "referenceRange": [ {
                    "text": "12.0-16.0"
                  } ]
                }
              }, {
                "resource": {
                  "resourceType": "Observation",
                  "id": "observation-2",
                  "status": "final",
                  "code": {
                    "coding": [ {
                      "system": "http://loinc.org",
                      "code": "11502-2",
                      "display": "Comentario"
                    } ],
                    "text": "Comentario"
                  },
                  "subject": {
                    "reference": "Patient/patient"
                  },
                  "valueString": "Muestra sin hemólisis"
                }
              } ]
            }""";

    static final String PROBLEM_EMPTY_BODY = """
            {
              "type": "about:blank",
              "title": "Mensaje vacío",
              "status": 400,
              "detail": "El cuerpo de la petición está vacío: envía un mensaje HL7 v2 en texto plano.",
              "code": "HL7_EMPTY_BODY"
            }""";

    static final String PROBLEM_UNSUPPORTED_FORMAT = """
            {
              "type": "about:blank",
              "title": "Formato de salida no soportado",
              "status": 400,
              "detail": "El formato «xml» no existe: usa «json» o «fhir».",
              "code": "HL7_UNSUPPORTED_FORMAT"
            }""";

    static final String PROBLEM_PAYLOAD_TOO_LARGE = """
            {
              "type": "about:blank",
              "title": "Mensaje demasiado grande",
              "status": 413,
              "detail": "El mensaje supera el máximo de 64 KB.",
              "code": "HL7_PAYLOAD_TOO_LARGE"
            }""";

    static final String PROBLEM_INVALID_MESSAGE = """
            {
              "type": "about:blank",
              "title": "Mensaje HL7 no válido",
              "status": 422,
              "detail": "El cuerpo no se pudo interpretar como un mensaje HL7 v2: comprueba que empieza por el segmento MSH y que usa los separadores estándar.",
              "code": "HL7_INVALID_MESSAGE"
            }""";

    static final String PROBLEM_UNSUPPORTED_MESSAGE = """
            {
              "type": "about:blank",
              "title": "Mensaje HL7 no soportado",
              "status": 422,
              "detail": "Se recibió ADT^A08 (v2.5.1). Esta demo solo traduce ADT^A01 y ORU^R01 en HL7 v2.5.1.",
              "code": "HL7_UNSUPPORTED_MESSAGE"
            }""";
}
