-- Generado por scripts/make-seed.py. No editar a mano.

-- architecture en
UPDATE content_version SET active = false WHERE kind = 'architecture' AND lang = 'en' AND active;
INSERT INTO content_version (kind, lang, version, content, active)
SELECT 'architecture', 'en', COALESCE(MAX(version), 0) + 1, $architecture$
{
  "core": {
    "name": "Domain",
    "description": "The business rules, unaware of what sits outside."
  },
  "ports": [
    {
      "id": "database",
      "name": "Database",
      "description": "Where the data is stored.",
      "adapters": [
        {
          "id": "postgresql",
          "name": "PostgreSQL",
          "status": "in-this-api"
        },
        {
          "id": "mongodb",
          "name": "MongoDB",
          "status": "production",
          "note": "Sample note"
        }
      ]
    },
    {
      "id": "queue",
      "name": "Queues",
      "adapters": [
        {
          "id": "kafka",
          "name": "Kafka",
          "status": "experiment"
        }
      ]
    }
  ]
}
$architecture$::jsonb, true
FROM content_version WHERE kind = 'architecture' AND lang = 'en';

-- architecture es
UPDATE content_version SET active = false WHERE kind = 'architecture' AND lang = 'es' AND active;
INSERT INTO content_version (kind, lang, version, content, active)
SELECT 'architecture', 'es', COALESCE(MAX(version), 0) + 1, $architecture$
{
  "core": {
    "name": "Dominio",
    "description": "Las reglas del negocio, sin saber qué hay fuera."
  },
  "ports": [
    {
      "id": "database",
      "name": "Base de datos",
      "description": "Dónde se guardan los datos.",
      "adapters": [
        {
          "id": "postgresql",
          "name": "PostgreSQL",
          "status": "in-this-api"
        },
        {
          "id": "mongodb",
          "name": "MongoDB",
          "status": "production",
          "note": "Nota de ejemplo"
        }
      ]
    },
    {
      "id": "queue",
      "name": "Colas",
      "adapters": [
        {
          "id": "kafka",
          "name": "Kafka",
          "status": "experiment"
        }
      ]
    }
  ]
}
$architecture$::jsonb, true
FROM content_version WHERE kind = 'architecture' AND lang = 'es';

-- projects en
UPDATE content_version SET active = false WHERE kind = 'projects' AND lang = 'en' AND active;
INSERT INTO content_version (kind, lang, version, content, active)
SELECT 'projects', 'en', COALESCE(MAX(version), 0) + 1, $projects$
{
  "projects": [
    {
      "id": "proyecto-publico",
      "title": "Sample public project",
      "summary": "A made-up product of my own, with the client named.",
      "relation": "own",
      "role": "Backend and web",
      "disclosure": "public",
      "client": "Ejemplo S.A.",
      "clientLabel": "made-up company",
      "startDate": "2022-01",
      "stack": [
        "Java",
        "Quarkus",
        "PostgreSQL"
      ],
      "highlights": [
        "A sample achievement"
      ],
      "links": [
        {
          "label": "See the project",
          "url": "https://example.com/proyecto"
        }
      ]
    },
    {
      "id": "proyecto-anonimo",
      "title": "Sample anonymous project",
      "summary": "Made-up work for a company that is not named.",
      "relation": "employer",
      "role": "Backend",
      "disclosure": "anonymous",
      "clientLabel": "sample health insurer",
      "startDate": "2019-03",
      "endDate": "2020-11",
      "stack": [
        "Java",
        "Oracle"
      ],
      "highlights": [],
      "links": []
    }
  ]
}
$projects$::jsonb, true
FROM content_version WHERE kind = 'projects' AND lang = 'en';

-- projects es
UPDATE content_version SET active = false WHERE kind = 'projects' AND lang = 'es' AND active;
INSERT INTO content_version (kind, lang, version, content, active)
SELECT 'projects', 'es', COALESCE(MAX(version), 0) + 1, $projects$
{
  "projects": [
    {
      "id": "proyecto-publico",
      "title": "Proyecto público de ejemplo",
      "summary": "Un producto propio ficticio, con el cliente nombrado.",
      "relation": "own",
      "role": "Backend y web",
      "disclosure": "public",
      "client": "Ejemplo S.A.",
      "clientLabel": "empresa ficticia",
      "startDate": "2022-01",
      "stack": [
        "Java",
        "Quarkus",
        "PostgreSQL"
      ],
      "highlights": [
        "Un logro de ejemplo"
      ],
      "links": [
        {
          "label": "Ver el proyecto",
          "url": "https://example.com/proyecto"
        }
      ]
    },
    {
      "id": "proyecto-anonimo",
      "title": "Proyecto anónimo de ejemplo",
      "summary": "Un trabajo ficticio para una empresa que no se nombra.",
      "relation": "employer",
      "role": "Backend",
      "disclosure": "anonymous",
      "clientLabel": "prepaga de ejemplo",
      "startDate": "2019-03",
      "endDate": "2020-11",
      "stack": [
        "Java",
        "Oracle"
      ],
      "highlights": [],
      "links": []
    }
  ]
}
$projects$::jsonb, true
FROM content_version WHERE kind = 'projects' AND lang = 'es';

-- resume en
UPDATE resume_version SET active = false WHERE lang = 'en' AND active;
INSERT INTO resume_version (lang, version, content, active)
SELECT 'en', COALESCE(MAX(version), 0) + 1, $resume$
{
  "$schema": "https://raw.githubusercontent.com/jsonresume/resume-schema/v1.0.0/schema.json",
  "meta": {
    "lang": "en",
    "version": "sample",
    "lastModified": "2026-01-01"
  },
  "basics": {
    "name": "Ada Ejemplo",
    "label": "Senior Backend Engineer · backend focus",
    "tagline": "Java/Quarkus · APIs and data",
    "email": "ada@example.com",
    "location": {
      "city": "Ciudad Ejemplo",
      "countryCode": "ES"
    },
    "summary": "Fictional profile for tests: designs APIs and data systems.",
    "availability": "Available for testing.",
    "profiles": [
      {
        "network": "LinkedIn",
        "url": "https://www.linkedin.com/in/ada-ejemplo"
      },
      {
        "network": "GitHub",
        "url": "https://github.com/ada-ejemplo"
      }
    ]
  },
  "impact": [
    {
      "value": "10,000+",
      "text": "daily requests on a sample API"
    },
    {
      "value": "+3",
      "text": "microservices delivered"
    }
  ],
  "work": [
    {
      "id": "acme-corp",
      "name": "Acme Corp",
      "position": "Backend Developer",
      "startDate": "2020-01",
      "summary": "Current stage at a fictional company.",
      "phases": [
        {
          "title": "Support and analysis",
          "client": "Cliente Uno"
        },
        {
          "title": "Microservices platform",
          "client": "Cliente Dos"
        }
      ],
      "highlights": [
        "REST API design with Quarkus.",
        "Automated tests in CI."
      ]
    },
    {
      "id": "globex",
      "name": "Globex",
      "position": "Developer",
      "startDate": "2017-03",
      "endDate": "2019-12",
      "summary": "Middle stage.",
      "highlights": [
        "Data migration."
      ]
    },
    {
      "id": "initech",
      "name": "Initech",
      "position": "Junior Developer",
      "startDate": "2014-06",
      "endDate": "2017-02",
      "summary": "First stage."
    }
  ],
  "education": [
    {
      "institution": "Universidad Ejemplo",
      "studyType": "Degree",
      "area": "Computer Science"
    }
  ],
  "certificates": [
    {
      "name": "Certified Example (EX-100)",
      "issuer": "Example Institute"
    }
  ],
  "courses": [
    {
      "name": "Quarkus",
      "issuer": "Example Academy"
    },
    {
      "name": "PostgreSQL",
      "issuer": "Example Academy"
    }
  ],
  "skills": [
    {
      "group": "backend",
      "name": "Backend",
      "keywords": [
        "Java 17+",
        "Quarkus",
        "Spring Boot"
      ]
    },
    {
      "group": "data",
      "name": "Data",
      "keywords": [
        "PostgreSQL",
        "Flyway"
      ]
    },
    {
      "group": "platform",
      "name": "Platform",
      "keywords": [
        "Docker",
        "GitHub Actions"
      ]
    }
  ],
  "languages": [
    {
      "language": "Spanish",
      "fluency": "Native"
    },
    {
      "language": "English",
      "fluency": "Basic"
    }
  ]
}
$resume$::jsonb, true
FROM resume_version WHERE lang = 'en';

-- resume es
UPDATE resume_version SET active = false WHERE lang = 'es' AND active;
INSERT INTO resume_version (lang, version, content, active)
SELECT 'es', COALESCE(MAX(version), 0) + 1, $resume$
{
  "$schema": "https://raw.githubusercontent.com/jsonresume/resume-schema/v1.0.0/schema.json",
  "meta": {
    "lang": "es",
    "version": "sample",
    "lastModified": "2026-01-01"
  },
  "basics": {
    "name": "Ada Ejemplo",
    "label": "Senior Backend Engineer · foco backend",
    "tagline": "Java/Quarkus · APIs y datos",
    "email": "ada@example.com",
    "location": {
      "city": "Ciudad Ejemplo",
      "countryCode": "ES"
    },
    "summary": "Perfil ficticio para las pruebas: diseña APIs y sistemas de datos.",
    "availability": "Disponible para pruebas.",
    "profiles": [
      {
        "network": "LinkedIn",
        "url": "https://www.linkedin.com/in/ada-ejemplo"
      },
      {
        "network": "GitHub",
        "url": "https://github.com/ada-ejemplo"
      }
    ]
  },
  "impact": [
    {
      "value": "+10.000",
      "text": "peticiones diarias en una API de ejemplo"
    },
    {
      "value": "+3",
      "text": "microservicios entregados"
    }
  ],
  "work": [
    {
      "id": "acme-corp",
      "name": "Acme Corp",
      "position": "Backend Developer",
      "startDate": "2020-01",
      "summary": "Etapa actual en una empresa ficticia.",
      "phases": [
        {
          "title": "Soporte y análisis",
          "client": "Cliente Uno"
        },
        {
          "title": "Plataforma de microservicios",
          "client": "Cliente Dos"
        }
      ],
      "highlights": [
        "Diseño de APIs REST con Quarkus.",
        "Pruebas automatizadas en CI."
      ]
    },
    {
      "id": "globex",
      "name": "Globex",
      "position": "Developer",
      "startDate": "2017-03",
      "endDate": "2019-12",
      "summary": "Etapa intermedia.",
      "highlights": [
        "Migración de datos."
      ]
    },
    {
      "id": "initech",
      "name": "Initech",
      "position": "Junior Developer",
      "startDate": "2014-06",
      "endDate": "2017-02",
      "summary": "Primera etapa."
    }
  ],
  "education": [
    {
      "institution": "Universidad Ejemplo",
      "studyType": "Grado",
      "area": "Informática"
    }
  ],
  "certificates": [
    {
      "name": "Certified Example (EX-100)",
      "issuer": "Example Institute"
    }
  ],
  "courses": [
    {
      "name": "Quarkus",
      "issuer": "Example Academy"
    },
    {
      "name": "PostgreSQL",
      "issuer": "Example Academy"
    }
  ],
  "skills": [
    {
      "group": "backend",
      "name": "Backend",
      "keywords": [
        "Java 17+",
        "Quarkus",
        "Spring Boot"
      ]
    },
    {
      "group": "data",
      "name": "Datos",
      "keywords": [
        "PostgreSQL",
        "Flyway"
      ]
    },
    {
      "group": "platform",
      "name": "Plataforma",
      "keywords": [
        "Docker",
        "GitHub Actions"
      ]
    }
  ],
  "languages": [
    {
      "language": "Español",
      "fluency": "Nativo"
    },
    {
      "language": "Inglés",
      "fluency": "Básico"
    }
  ]
}
$resume$::jsonb, true
FROM resume_version WHERE lang = 'es';

-- services en
UPDATE content_version SET active = false WHERE kind = 'services' AND lang = 'en' AND active;
INSERT INTO content_version (kind, lang, version, content, active)
SELECT 'services', 'en', COALESCE(MAX(version), 0) + 1, $services$
{
  "services": [
    {
      "id": "servicio-ejemplo",
      "title": "Sample service",
      "summary": "A made-up service with a price, for the tests.",
      "price": {
        "from": 1500,
        "currency": "EUR"
      }
    },
    {
      "id": "servicio-a-consultar",
      "title": "Service on request",
      "summary": "A made-up service with no published price.",
      "price": null
    }
  ]
}
$services$::jsonb, true
FROM content_version WHERE kind = 'services' AND lang = 'en';

-- services es
UPDATE content_version SET active = false WHERE kind = 'services' AND lang = 'es' AND active;
INSERT INTO content_version (kind, lang, version, content, active)
SELECT 'services', 'es', COALESCE(MAX(version), 0) + 1, $services$
{
  "services": [
    {
      "id": "servicio-ejemplo",
      "title": "Servicio de ejemplo",
      "summary": "Un servicio ficticio con precio, para las pruebas.",
      "price": {
        "from": 1500,
        "currency": "EUR"
      }
    },
    {
      "id": "servicio-a-consultar",
      "title": "Servicio a consultar",
      "summary": "Un servicio ficticio sin precio publicado.",
      "price": null
    }
  ]
}
$services$::jsonb, true
FROM content_version WHERE kind = 'services' AND lang = 'es';
