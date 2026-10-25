# 🎬 CineSystem — Microservicios Java con Monitoreo en Kubernetes

Sistema de reserva de boletos para cines implementado como microservicios Java con monitoreo completo en Kubernetes usando Prometheus y Grafana.

## 🏢 Dominio de Negocio

| Servicio | Puerto | Descripción |
|---|---|---|
| `movie-catalog-service` | 8081 | Gestiona películas, funciones y disponibilidad de asientos en memoria |
| `ticketing-service` | 8082 | Procesa compras de boletos, consultando al catalog-service vía HTTP |

**Regla de negocio:** Antes de confirmar una compra, `ticketing-service` consulta a `movie-catalog-service` para verificar y reservar asientos. Si el catalog-service no responde en **2000ms**, la compra falla de forma controlada.

---

## 📁 Estructura del Repositorio

```
microservicios/
├── microservices/
│   ├── movie-catalog-service/     # Servicio A - Catálogo
│   │   ├── pom.xml
│   │   ├── Dockerfile
│   │   └── src/main/java/com/cinesystem/catalog/
│   └── ticketing-service/         # Servicio B - Boletería
│       ├── pom.xml
│       ├── Dockerfile
│       └── src/main/java/com/cinesystem/ticketing/
├── k8s/
│   ├── namespace.yaml
│   ├── values-prom-stack.yaml     # Config del chart Helm
│   ├── catalog-service/           # Deployment + Service
│   ├── ticketing-service/         # Deployment + Service
│   ├── servicemonitors/           # ServiceMonitor para Prometheus
│   └── prometheus-rules.yaml      # Alertas PromQL
├── load-testing/
│   └── stress.sh                  # Script de carga parametrizable
├── scripts/
│   ├── setup.sh                   # Automatización completa
│   └── teardown.sh                # Limpieza de recursos
└── docs/
    ├── informe.md                 # Informe técnico completo
    └── dashboard.json             # Dashboard exportado de Grafana
```

---

## 🛠️ Instalación de Herramientas (Windows)

### Docker Desktop
```powershell
winget install Docker.DockerDesktop
# Reiniciar Windows después de la instalación
```

### Minikube
```powershell
winget install Kubernetes.minikube
# Verificar:
minikube version
```

### kubectl
```powershell
winget install Kubernetes.kubectl
# Verificar:
kubectl version --client
```

### Helm
```powershell
winget install Helm.Helm
# Verificar:
helm version
```

---

## 🚀 Despliegue Automático

```bash
# Clonar el repositorio y ejecutar:
bash scripts/setup.sh
```

El script automatiza todo:
1. Verifica prerequisitos
2. Inicia Minikube con 4 CPUs y 6GB RAM
3. Instala Prometheus + Grafana con Helm
4. Apunta Docker al daemon de Minikube
5. Construye las imágenes con multi-stage build
6. Aplica todos los manifiestos
7. Muestra las URLs de acceso

---

## 🌐 URLs de Acceso

> Obtener IP con: `minikube ip`

| Servicio | URL | Credenciales |
|---|---|---|
| **Grafana** | `http://<MINIKUBE_IP>:32000` | admin / CineSystem2024! |
| **Prometheus** | `http://<MINIKUBE_IP>:32001` | — |
| **AlertManager** | `http://<MINIKUBE_IP>:32002` | — |
| **Catalog API** | `http://<MINIKUBE_IP>:30081/api/movies` | — |
| **Ticketing API** | `http://<MINIKUBE_IP>:30082/api/tickets/summary` | — |
| **Catalog /metrics** | `http://<MINIKUBE_IP>:30081/metrics` | — |
| **Ticketing /metrics** | `http://<MINIKUBE_IP>:30082/metrics` | — |

---

## 🔌 Endpoints de la API

### movie-catalog-service (puerto 30081)

| Método | Ruta | Descripción |
|---|---|---|
| `GET` | `/api/movies` | Listar toda la cartelera |
| `GET` | `/api/movies/{id}/availability` | Ver asientos disponibles por función |
| `POST` | `/api/movies/{id}/reserve-seats` | Reservar asientos (usado por ticketing) |
| `POST` | `/api/movies/{id}/release-seats` | Liberar asientos (al cancelar) |
| `POST` | `/api/admin/delay` | Configurar delay artificial (Casuística 2) |
| `GET` | `/metrics` | Métricas de Prometheus |
| `GET` | `/actuator/health` | Health check |

### ticketing-service (puerto 30082)

| Método | Ruta | Descripción |
|---|---|---|
| `POST` | `/api/tickets/purchase` | Comprar boleto (llama al catalog-service) |
| `GET` | `/api/tickets/{ticketId}` | Consultar boleto por ID |
| `GET` | `/api/tickets/summary` | Resumen de ventas del día |
| `POST` | `/api/tickets/cancel` | Cancelar boleto y liberar asientos |
| `GET` | `/metrics` | Métricas de Prometheus |
| `GET` | `/actuator/health` | Health check |

---

## 📊 Métricas Registradas

### movie-catalog-service
| Métrica | Tipo | Descripción |
|---|---|---|
| `catalog_movies_requested_total` | Counter | Total de consultas al catálogo |
| `catalog_seat_reservations_success_total` | Counter | Reservas exitosas |
| `catalog_seat_reservations_failed_total` | Counter | Reservas fallidas |
| `catalog_artificial_delay_milliseconds` | Gauge | Delay artificial actual (ms) |
| `catalog_available_seats_total` | Gauge | Total asientos disponibles en cartelera |
| `catalog_response_time_seconds` | Timer | Tiempo de respuesta con percentiles |

### ticketing-service
| Métrica | Tipo | Descripción |
|---|---|---|
| `ticketing_tickets_sold_total` | Counter | Boletos vendidos exitosamente |
| `ticketing_purchase_failures_total` | Counter | Fallos de compra (con tag `reason`) |
| `ticketing_cancellations_total` | Counter | Boletos cancelados |
| `ticketing_active_reservations_gauge` | Gauge | Reservas activas en el sistema |
| `ticketing_daily_revenue_cents` | Gauge | Ingresos del día en centavos |
| `ticketing_checkout_duration_seconds` | Timer | Duración del proceso de compra completo |

---

## 🚨 Alertas Configuradas

| Alerta | Condición | Severidad |
|---|---|---|
| `MovieCatalogServiceDown` | catalog-service DOWN por 1 minuto | critical |
| `TicketingServiceDown` | ticketing-service DOWN por 1 minuto | critical |
| `HighCheckoutFailureRate` | Tasa de fallos > 15% en 2 minutos | warning |
| `CinemaSoldOutAlert` | Asientos disponibles < 50 | warning |
| `CatalogHighLatency` | p95 de latencia > 1.5s por 2 minutos | warning |

---

## 🧪 Pruebas de Carga

```bash
# Estresar ambos servicios a 10 RPS durante 2 minutos
bash load-testing/stress.sh --target both --rps 10 --duration 120

# Solo ticketing (para Casuística 3 - overbooking)
bash load-testing/stress.sh --target ticketing --rps 30 --duration 60

# Solo catálogo con RPS bajo
bash load-testing/stress.sh --target catalog --rps 5 --duration 300
```

---

## 🔴 Casuísticas

### Casuística 1 — Servicio Caído
```bash
# Activar (inyectar comando inválido en el pod)
kubectl patch deployment ticketing-service -n cine-system \
  --patch '{"spec":{"template":{"spec":{"containers":[{"name":"ticketing-service","command":["java","-jar","/app/no-existe.jar"]}]}}}}'

# Resolver
kubectl rollout undo deployment/ticketing-service -n cine-system
```

### Casuística 2 — Servicio Lento
```bash
# Activar (3000ms > 2000ms timeout en ticketing)
curl -X POST http://$(minikube ip):30081/api/admin/delay \
  -H "Content-Type: application/json" \
  -d '{"delayMs": 3000}'

# Resolver
curl -X POST http://$(minikube ip):30081/api/admin/delay \
  -H "Content-Type: application/json" \
  -d '{"delayMs": 0}'
```

### Casuística 3 — Agotamiento de Asientos
```bash
# Activar (compras masivas hasta agotar la función IMAX)
bash load-testing/stress.sh --target ticketing --rps 30 --duration 30

# Resolver (reiniciar el pod resetea los datos en memoria)
kubectl rollout restart deployment/movie-catalog-service -n cine-system
```

---

## 🧹 Limpieza

```bash
# Eliminar recursos pero mantener el cluster
bash scripts/teardown.sh

# Eliminar TODO incluyendo Minikube
bash scripts/teardown.sh --purge-cluster
```

---

## 🔧 Tecnologías

- **Java 17** + **Spring Boot 3.2.5**
- **Micrometer** + **Prometheus Registry**
- **Spring WebClient** (comunicación entre servicios)
- **Maven** (gestión de dependencias)
- **Docker** (multi-stage build)
- **Minikube** (cluster Kubernetes local)
- **Helm** + **kube-prometheus-stack**
- **Grafana** + **Prometheus** + **AlertManager**
