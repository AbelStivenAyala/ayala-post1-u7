Post-contenido — Unidad 7: Patrones Arquitectónicos I

Autor: Abel Stiven Ayala Llanes
Curso: Patrones de Diseño de Software — UFPS

Descripción

Repositorio del post-contenido de la Unidad 7. Contiene un único proyecto Spring Boot (multas-biblioteca-api) que implementa el backend de un sistema de gestión de multas de biblioteca universitaria, en dos partes:

Parte 1: API REST con arquitectura en capas (Model, Repository, Service, Controller) sobre Spring Data JPA y H2. Una multa se registra cuando un estudiante devuelve un libro con retraso, se calcula su monto y se marca como pagada.
Parte 2: pago en línea de multas con dos pasarelas intercambiables por configuración (PagosUDES y Wompi), resuelto con un puerto de dominio y dos adaptadores (arquitectura hexagonal aplicada solo a esta porción).
Estructura de paquetes
multas-biblioteca-api/
└── src/main/java/com/example/multas/
    ├── controller/        MultaController, GlobalExceptionHandler, GenerarMultaRequest
    ├── service/           MultaService
    ├── model/             Multa, EstadoMulta, excepciones de negocio
    ├── repository/        MultaRepository
    ├── domain/            (Parte 2, sin Spring)
    │   ├── port/          PasarelaPagoPort
    │   ├── ResultadoPago
    │   └── PagoRechazadoException
    ├── infrastructure/    (Parte 2)
    │   ├── pago/          PagosUdesAdapter, WompiAdapter
    │   └── config/        RestTemplateConfig
    └── mock/              Pasarelas simuladas, solo para pruebas locales
Parte 1 — Arquitectura en Capas
Controller (Presentación): expone /api/multas, valida la entrada con @Valid y traduce excepciones de negocio a códigos HTTP en GlobalExceptionHandler. Nunca accede al repositorio directamente.
Service (Aplicación): MultaService orquesta los casos de uso y aplica la regla del límite de multas pendientes. No conoce detalles HTTP.
Model (Dominio): la entidad Multa contiene su propio comportamiento: calcularMonto (días de atraso × $500, con tope de $15.000) y marcarComoPagada.
Repository (Infraestructura): MultaRepository extiende JpaRepository y agrega countByEstudianteIdAndEstado.
Endpoints
Método	Ruta	Descripción	Respuestas
GET	/api/multas	Listar todas	200
GET	/api/multas/{id}	Buscar por ID	200, 404
GET	/api/multas/estudiante/{estudianteId}	Listar por estudiante	200
POST	/api/multas	Generar multa	201, 400, 409
PATCH	/api/multas/{id}/pagar	Pagar en ventanilla	200, 404, 409
POST	/api/multas/{id}/pagar-en-linea	Pagar por la pasarela activa	200, 402, 404, 409
Parte 2 — Pago en Línea con Dos Pasarelas

Opción elegida: C — puerto de dominio con dos adaptadores (hexagonal solo en la porción de pago).

PasarelaPagoPort y ResultadoPago viven en domain/ sin ninguna dependencia de Spring ni de clientes HTTP. PagosUdesAdapter y WompiAdapter (en infrastructure/pago/) implementan el puerto y traducen el formato HTTP de cada pasarela al mismo ResultadoPago. El adaptador activo se selecciona con la propiedad app.pagos.proveedor (pagosudes o wompi), sin modificar MultaController ni MultaService.

Las partes de la Parte 1 (Multa, MultaRepository, MultaController) no se migraron a hexagonal: solo se agregó lo necesario para el pago en línea.

Cómo ejecutar

Requisitos: JDK 17 o superior y Maven 3.8+.

bash
cd multas-biblioteca-api
mvn clean package
mvn spring-boot:run

La aplicación queda en http://localhost:8080 y la consola H2 en /h2-console.

Nota (Windows): si la ruta del proyecto contiene caracteres como la ñ, mvn spring-boot:run puede fallar con ClassNotFoundException. Se soluciona ejecutando desde una ruta sin caracteres especiales.

Pasarelas simuladas: con app.pagos.mock.enabled=true (valor por defecto), la propia aplicación expone /mock/pagosudes/transacciones y /mock/wompi/transactions, que rechazan cualquier pago de más de $10.000 (multas de 21 días o más). Para usar pasarelas reales o externas, cambiar las URLs en application.properties.

Cambiar de pasarela: en application.properties, app.pagos.proveedor=wompi y reiniciar.

Herramientas utilizadas
Java 17, Spring Boot 3.3.5, Spring Data JPA, H2, Bean Validation, RestTemplate
Apache Maven, curl / PowerShell, Git, GitHub
Decisiones de diseño
Punto de decisión 1 — Cálculo del monto: ¿entidad o Service?

El cálculo del monto (Multa.calcularMonto) vive como método estático de la entidad Multa. El criterio que usé: si una regla no necesita ningún colaborador externo (Repository, otro Service) y solo depende de datos que ya recibe como parámetro, es candidata a vivir en el propio objeto de dominio. El monto es exactamente ese caso: depende solo de los días de atraso.

La alternativa era un método privado de MultaService. Habría funcionado, pero dejaría a Multa como un simple contenedor de datos sin comportamiento (modelo anémico), y cualquier otro punto del sistema que necesitara recalcular un monto tendría que duplicar la fórmula o pasar por el Service para algo que no requiere ninguna dependencia. Al ponerlo en la entidad, la regla tiene un único lugar y se puede probar sin levantar Spring (ver MultaTest).

Punto de decisión 2 — Conteo de multas pendientes: ¿consulta o filtrado en memoria?

El límite de 3 multas pendientes se resuelve con MultaRepository.countByEstudianteIdAndEstado, que Spring Data traduce a un COUNT en SQL. La alternativa era traer todas las multas del estudiante con findByEstudianteId y filtrar con streams.

La decisión de negocio ("¿se le permite generar una multa más?") la toma exclusivamente MultaService, pero el dato que necesita ("¿cuántas tiene pendientes?") se resuelve donde es eficiente: en el motor de base de datos. Con pocos datos de prueba no se nota diferencia, pero si el historial creciera a miles de multas por estudiante, la versión en memoria cargaría y mapearía todas las filas a objetos Java en cada creación, y el tiempo de respuesta crecería proporcionalmente al historial. El COUNT devuelve un solo número sin importar el tamaño del historial.

Punto de decisión 3 — Selección del adaptador activo

Elegí @ConditionalOnProperty en cada adaptador: según app.pagos.proveedor, solo uno de los dos beans existe en el contexto de Spring, así que MultaService pide por constructor un único PasarelaPagoPort sin @Qualifier ni lógica condicional propia.

La alternativa era inyectar un Map<String, PasarelaPagoPort> y elegir la clave en tiempo de ejecución. Es válida y más flexible, porque permite cambiar de proveedor sin reiniciar. La descarté porque el requisito real es una pasarela fija por sede durante el piloto, que no necesita esa flexibilidad, y porque habría obligado a MultaService a conocer las claves de configuración de cada proveedor. El costo de mi elección es que cambiar de pasarela exige reiniciar la aplicación.

Punto de decisión 4 — Diseño del puerto y el tipo de resultado

PasarelaPagoPort.procesar devuelve ResultadoPago, un tipo de dominio neutral, y no el DTO de ninguna pasarela. Ambos adaptadores reciben formatos distintos (idTransaccion/estadoTransaccion en PagosUDES; reference/status con montos en centavos en Wompi), pero los traducen al mismo ResultadoPago antes de devolverlo.

Si el puerto devolviera el DTO específico de cada pasarela, o tuviera un método por proveedor, MultaService tendría que conocer y distinguir ambos formatos, y agregar una tercera pasarela obligaría a modificar el Service además de escribir un adaptador. Por la misma razón, el campo se llama referenciaExterna y no idTransaccion: ese nombre es el vocabulario de PagosUDES. Con él, WompiAdapter tendría que inventar un valor o dejar un nombre que no describe lo que Wompi realmente devuelve (reference), señal de que el tipo de dominio no sería neutral respecto a los proveedores.

Trade-off considerado — Parte 2

Elegí la Opción C (puerto de dominio con adaptadores) y descarté la A (rama condicional en MultaService) y la B (interfaz Strategy dentro de service/).

A favor de la opción elegida:

Los contratos HTTP son distintos. PagosUDES y Wompi difieren en nombres de campos, en unidades (pesos frente a centavos) y en el formato del estado. Con el puerto, esa traducción queda encerrada en cada adaptador y no se filtra hacia el Service.
El número de proveedores es inestable. El enunciado anticipa que, terminado el piloto, puede agregarse una tercera pasarela o eliminarse una. Con el puerto, agregar o quitar un proveedor es crear o borrar un adaptador y cambiar una propiedad, sin tocar MultaService.
Testabilidad. MultaService depende de una interfaz, por lo que se puede probar con una implementación falsa del puerto, sin red ni pasarelas.

En contra de la opción descartada (A): con un if/switch dentro de MultaService, el Service pasaría a conocer los detalles HTTP de ambas pasarelas (URLs, DTOs, conversión a centavos), mezclando orquestación de casos de uso con integración externa, y crecería con cada proveedor nuevo. Frente a la B: una interfaz Strategy en service/ también resuelve la intercambiabilidad, pero deja las implementaciones, con su HTTP y sus DTOs, dentro de la capa de servicio.

Qué costó la opción elegida: dos paquetes nuevos (domain/ e infrastructure/), unas siete clases adicionales, un nivel más de indirección y una curva de aprendizaje mayor que con A o B. Para solo dos proveedores estables, la Opción B habría sido una alternativa razonable y más barata. Además, el puerto no es completamente puro: PasarelaPagoPort recibe una Multa, que lleva anotaciones jakarta.persistence. Cumple la condición de no depender de Spring, pero acopla el dominio de pago al modelo de persistencia.

¿Revertiría si el piloto terminara y quedara una sola pasarela? Probablemente no del todo. Eliminar el adaptador sobrante es trivial, y mantener el puerto cuesta poco; lo que sí consideraría es simplificar si no volviera a haber proveedores nuevos. Si el piloto confirma que solo habrá una pasarela de forma permanente, el puerto dejaría de justificar su costo, y volver a una clase de servicio concreta sería razonable.

Limitación conocida: pagarConPasarela realiza la llamada HTTP dentro de una transacción (@Transactional en la clase). Para el alcance del laboratorio es aceptable, pero en producción convendría sacar la llamada externa de la transacción.

Capturas de los endpoints probados

Las capturas están en la carpeta capturas/.

Parte 1
Prueba	Captura
Lista vacía (200)	lista vacía
Crear multa (201)	crear multa
Datos inválidos (400)	400
ID inexistente (404)	404
Pagar en ventanilla (200)	ventanilla
Pagar de nuevo (409)	409 pagada
Cuarta multa pendiente (409)	409 límite
Parte 2
Prueba	Captura
Pago en línea con PagosUDES (200)	pagosudes
Pago rechazado (402)	402
Ya pagada, en línea (409)	409 en línea
Cambio a Wompi (200)	wompi
Wompi rechazado (402)	wompi 402

Conclusiones

En la Parte 1 aprendí a decidir dónde vive cada regla de negocio: lo que solo depende de los datos del propio objeto va en la entidad, y lo que necesita datos externos se orquesta en el Service, delegando a la base de datos el trabajo que ella hace mejor. En la Parte 2, lo que más me costó fue decidir entre extender las capas o introducir el puerto, porque la Opción B también resolvía la intercambiabilidad con menos código. Me incliné por el puerto porque cada pasarela tiene un contrato HTTP distinto y porque el número de proveedores podía cambiar. Entendí que una arquitectura más elaborada se justifica por un requisito concreto de cambio, no por el patrón en sí.