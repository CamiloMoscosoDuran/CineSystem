#!/usr/bin/env bash
# =============================================================================
# setup.sh — Script de automatización completa del entorno CineSystem
# =============================================================================
# Uso: bash scripts/setup.sh
#
# Este script levanta todo el sistema desde cero:
# 1. Verifica prerequisitos (Docker, Minikube, kubectl, Helm)
# 2. Inicia el cluster Minikube con recursos suficientes
# 3. Habilita addons necesarios (metrics-server, ingress)
# 4. Instala el stack de monitoreo con Helm (Prometheus + Grafana)
# 5. Apunta Docker al daemon interno de Minikube
# 6. Construye las imágenes de los microservicios
# 7. Aplica todos los manifiestos de Kubernetes
# 8. Espera a que los pods estén Running
# 9. Muestra las URLs de acceso
# =============================================================================

set -euo pipefail

# Agregar rutas estándar de Windows en caso de que la terminal no haya actualizado el PATH
export PATH="$PATH:/c/Program Files/Kubernetes/Minikube"
export PATH="$PATH:/c/Users/${USER:-Usuario}/AppData/Local/Microsoft/WinGet/Links"
export PATH="$PATH:/c/Users/${USER:-Usuario}/AppData/Local/Microsoft/WinGet/Packages/Helm.Helm_Microsoft.Winget.Source_8wekyb3d8bbwe/windows-amd64"

# Colores para output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color

# Función de log
log_info()    { echo -e "${GREEN}[INFO]${NC} $1"; }
log_warn()    { echo -e "${YELLOW}[WARN]${NC} $1"; }
log_error()   { echo -e "${RED}[ERROR]${NC} $1"; }
log_step()    { echo -e "\n${BLUE}━━━ PASO: $1 ━━━${NC}"; }
log_success() { echo -e "${GREEN}[✓]${NC} $1"; }

# Directorio raíz del proyecto (script debe ejecutarse desde la raíz)
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
cd "${PROJECT_ROOT}"

echo -e "${CYAN}"
echo "╔═══════════════════════════════════════════════════╗"
echo "║     🎬 CineSystem — Setup Automático               ║"
echo "║     Kubernetes + Prometheus + Grafana              ║"
echo "╚═══════════════════════════════════════════════════╝"
echo -e "${NC}"

# =============================================================================
# PASO 1: Verificar prerequisitos
# =============================================================================
log_step "Verificando prerequisitos"

check_command() {
    if command -v "$1" &> /dev/null; then
        log_success "$1 encontrado: $(command -v "$1")"
    else
        log_error "$1 NO está instalado. Por favor instálalo primero."
        echo "  → Docker Desktop: https://www.docker.com/products/docker-desktop/"
        echo "  → Minikube:       https://minikube.sigs.k8s.io/docs/start/"
        echo "  → kubectl:        https://kubernetes.io/docs/tasks/tools/"
        echo "  → Helm:           https://helm.sh/docs/intro/install/"
        exit 1
    fi
}

check_command "docker"
check_command "minikube"
check_command "kubectl"
check_command "helm"

# Verificar que Docker está corriendo
if ! docker info &> /dev/null; then
    log_error "Docker no está corriendo. Inicia Docker Desktop primero."
    exit 1
fi
log_success "Docker está corriendo"

# =============================================================================
# PASO 2: Iniciar Minikube
# =============================================================================
log_step "Iniciando Minikube"

MINIKUBE_STATUS=$(minikube status --format='{{.Host}}' 2>/dev/null || echo "Stopped")

if [ "${MINIKUBE_STATUS}" = "Running" ]; then
    log_info "Minikube ya está corriendo. Continuando..."
else
    log_info "Iniciando Minikube con recursos suficientes para el stack completo..."
    minikube start \
        --cpus=4 \
        --memory=6144 \
        --disk-size=20g \
        --driver=docker \
        --kubernetes-version=stable
    log_success "Minikube iniciado correctamente"
fi

# =============================================================================
# PASO 3: Habilitar addons
# =============================================================================
log_step "Habilitando addons de Minikube"

log_info "Habilitando metrics-server..."
minikube addons enable metrics-server
log_success "metrics-server habilitado"

# =============================================================================
# PASO 4: Instalar stack de monitoreo con Helm
# =============================================================================
log_step "Instalando stack de monitoreo (Prometheus + Grafana)"

# Agregar repositorio de prometheus-community
log_info "Agregando repositorio prometheus-community..."
helm repo add prometheus-community https://prometheus-community.github.io/helm-charts 2>/dev/null || true
helm repo update

# Crear namespace de monitoring si no existe
kubectl create namespace monitoring --dry-run=client -o yaml | kubectl apply -f -

# Verificar si ya está instalado
if helm status monitoring -n monitoring &> /dev/null; then
    log_warn "Stack de monitoreo ya instalado. Actualizando..."
    helm upgrade monitoring prometheus-community/kube-prometheus-stack \
        -n monitoring \
        -f k8s/values-prom-stack.yaml \
        --wait \
        --timeout=10m
else
    log_info "Instalando kube-prometheus-stack..."
    helm install monitoring prometheus-community/kube-prometheus-stack \
        -n monitoring \
        --create-namespace \
        -f k8s/values-prom-stack.yaml \
        --wait \
        --timeout=10m
fi

log_success "Stack de monitoreo instalado"

# =============================================================================
# PASO 5 & 6: Construir imágenes Docker y cargarlas en Minikube
# =============================================================================
log_step "Construyendo y cargando imágenes Docker en Minikube"

log_info "Construyendo movie-catalog-service:1.0.0..."
docker build -t movie-catalog-service:1.0.0 ./microservices/movie-catalog-service/
log_success "Imagen movie-catalog-service:1.0.0 construida"

log_info "Cargando movie-catalog-service:1.0.0 en el cluster Minikube..."
minikube image load movie-catalog-service:1.0.0
log_success "Imagen movie-catalog-service:1.0.0 cargada en Minikube"

log_info "Construyendo ticketing-service:1.0.0..."
docker build -t ticketing-service:1.0.0 ./microservices/ticketing-service/
log_success "Imagen ticketing-service:1.0.0 construida"

log_info "Cargando ticketing-service:1.0.0 en el cluster Minikube..."
minikube image load ticketing-service:1.0.0
log_success "Imagen ticketing-service:1.0.0 cargada en Minikube"

# =============================================================================
# PASO 7: Aplicar manifiestos de Kubernetes
# =============================================================================
log_step "Aplicando manifiestos de Kubernetes"

log_info "Creando namespace cine-system..."
kubectl apply -f k8s/namespace.yaml

log_info "Desplegando movie-catalog-service..."
kubectl apply -f k8s/catalog-service/

log_info "Desplegando ticketing-service..."
kubectl apply -f k8s/ticketing-service/

log_info "Aplicando ServiceMonitors..."
kubectl apply -f k8s/servicemonitors/

log_info "Aplicando reglas de alerta de Prometheus..."
kubectl apply -f k8s/prometheus-rules.yaml

log_success "Manifiestos aplicados"

# =============================================================================
# PASO 8: Esperar a que los pods estén Running
# =============================================================================
log_step "Esperando a que los pods estén listos"

log_info "Esperando movie-catalog-service..."
kubectl rollout status deployment/movie-catalog-service -n cine-system --timeout=300s
log_success "movie-catalog-service: Running"

log_info "Esperando ticketing-service..."
kubectl rollout status deployment/ticketing-service -n cine-system --timeout=300s
log_success "ticketing-service: Running"

# =============================================================================
# PASO 9: Mostrar estado y URLs
# =============================================================================
log_step "Estado del sistema"

echo ""
log_info "Pods en cine-system:"
kubectl get pods -n cine-system
echo ""
log_info "Servicios en cine-system:"
kubectl get services -n cine-system
echo ""

# Obtener la IP de Minikube
MINIKUBE_IP=$(minikube ip)

echo ""
echo -e "${CYAN}╔═══════════════════════════════════════════════════════════╗${NC}"
echo -e "${CYAN}║                  🎬 URLS DE ACCESO                       ║${NC}"
echo -e "${CYAN}╠═══════════════════════════════════════════════════════════╣${NC}"
echo -e "${CYAN}║${NC} Grafana:              http://${MINIKUBE_IP}:32000               ${CYAN}║${NC}"
echo -e "${CYAN}║${NC}   Usuario: admin      Password: CineSystem2024!        ${CYAN}║${NC}"
echo -e "${CYAN}╠═══════════════════════════════════════════════════════════╣${NC}"
echo -e "${CYAN}║${NC} Prometheus:           http://${MINIKUBE_IP}:32001               ${CYAN}║${NC}"
echo -e "${CYAN}╠═══════════════════════════════════════════════════════════╣${NC}"
echo -e "${CYAN}║${NC} AlertManager:         http://${MINIKUBE_IP}:32002               ${CYAN}║${NC}"
echo -e "${CYAN}╠═══════════════════════════════════════════════════════════╣${NC}"
echo -e "${CYAN}║${NC} movie-catalog API:    http://${MINIKUBE_IP}:30081/api/movies    ${CYAN}║${NC}"
echo -e "${CYAN}║${NC} ticketing API:        http://${MINIKUBE_IP}:30082/api/tickets   ${CYAN}║${NC}"
echo -e "${CYAN}║${NC} catalog /metrics:     http://${MINIKUBE_IP}:30081/metrics       ${CYAN}║${NC}"
echo -e "${CYAN}║${NC} ticketing /metrics:   http://${MINIKUBE_IP}:30082/metrics       ${CYAN}║${NC}"
echo -e "${CYAN}╚═══════════════════════════════════════════════════════════╝${NC}"
echo ""
log_success "¡Sistema CineSystem desplegado correctamente! 🎉"
