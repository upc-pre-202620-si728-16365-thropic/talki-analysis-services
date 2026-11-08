# language: es
Característica: Consulta del feedback de una práctica
  Como estudiante
  quiero consultar el reporte de mi práctica
  para entender mi desempeño sin recibir puntuaciones inventadas

  # US15, US16, TS03, TS04
  Escenario: El propietario consulta una evaluación disponible
    Dado que "ana" cerró una práctica
    Y el análisis de esa práctica encontró 3 muletillas en 14 palabras
    Cuando "ana" consulta el reporte de esa práctica
    Entonces recibe la respuesta 200
    Y el estado del análisis es "COMPLETED"
    Y el reporte incluye puntuación, muletillas, versiones y limitaciones
    Y se publicó 1 evaluación

  # US37, TS04
  Escenario: El análisis aún no termina
    Dado que "ana" cerró una práctica
    Cuando "ana" consulta el reporte de esa práctica
    Entonces recibe la respuesta 202
    Y el estado del análisis es "PENDING"
    Y el reporte no incluye puntuación
    Y se publicó 0 evaluaciones

  # US15
  Escenario: Una práctica sin palabras no recibe puntuación
    Dado que "ana" cerró una práctica
    Y el análisis de esa práctica encontró 0 muletillas en 0 palabras
    Cuando "ana" consulta el reporte de esa práctica
    Entonces recibe la respuesta 200
    Y el estado del análisis es "INSUFFICIENT_EVIDENCE"
    Y el reporte no incluye puntuación
    Y se publicó 0 evaluaciones

  # TS03
  Escenario: Un análisis repetido no genera otra evaluación
    Dado que "ana" cerró una práctica
    Y el análisis de esa práctica encontró 3 muletillas en 14 palabras
    Y el mismo análisis vuelve a llegar
    Cuando "ana" consulta el reporte de esa práctica
    Entonces recibe la respuesta 200
    Y se publicó 1 evaluación

  # US20, TS04
  Escenario: Otro estudiante no accede al reporte
    Dado que "ana" cerró una práctica
    Y el análisis de esa práctica encontró 3 muletillas en 14 palabras
    Cuando "luis" consulta el reporte de esa práctica
    Entonces recibe la respuesta 404
    Y el reporte no incluye puntuación
