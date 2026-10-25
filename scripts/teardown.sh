#!/usr/bin/env bash
# =============================================================================
# teardown.sh — Script de limpieza del entorno CineSystem
# =============================================================================
# Uso:
#   bash scripts/teardown.sh              # Elimina recursos pero mantiene el cluster
#   bash scripts/teardown.sh --purge-cluster  # Elimina todo, incluyendo Minikube
# =============================================================================

set -euo pipefail

# Agregar rutas estándar de Windows en caso de que la terminal no haya actualizado el PATH
export PATH="$PATH:/c/Program Files/Kubernetes/Minikube"
export PATH="$PATH:/c/Users/${USER:-Usuario}/AppData/Local/Microsoft/WinGet/Links"
export PATH="$PATH:/c/Users/${USER:-Usuario}/AppData/Local/Microsoft/WinGet/Packages/Helm.Helm_Microsoft.Winget.Source_8wekyb3d8bbwe/windows-amd64"

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
NC='\033[0m'

log_info()  { echo -e "${GREEN}[INFO]${NC} $1"; }
log_warn()  { echo -e "${YELLOW}[WARN]${NC} $1"; }
log_step()  { echo -e "\n${BLUE}━━━ PASO: $1 ━━━${NC}"; }

PURGE_CLUSTER=false

# Parsear argumentos
for arg in "$@"; do
    case $arg in
        --purge-cluster)
            PURGE_CLUSTER=true
            shift
            ;;
    esac
done

echo -e "${CYAN}"
echo "╔═══════════════════════════════════════════════════╗"
echo "║     🎬 CineSystem — Teardown                      ║"
echo -e "╚═══════════════════════════════════════════════════╝${NC}"

if [ "${PURGE_CLUSTER}" = true ]; then
    log_warn "Modo --purge-cluster: Se eliminará el cluster completo de Minikube"
fi

# =============================================================================
# Eliminar manifiestos de Kubernetes
# =============================================================================
log_step "Eliminando manifiestos de Kubernetes"

log_info "Eliminando reglas de alerta..."
kubectl delete -f k8s/prometheus-rules.yaml --ignore-not-found=true

log_info "Eliminando ServiceMonitors..."
kubectl delete -f k8s/servicemonitors/ --ignore-not-found=true

log_info "Eliminando ticketing-service..."
kubectl delete -f k8s/ticketing-service/ --ignore-not-found=true

log_info "Eliminando movie-catalog-service..."
kubectl delete -f k8s/catalog-service/ --ignore-not-found=true

log_info "Eliminando namespace cine-system..."
kubectl delete namespace cine-system --ignore-not-found=true

# =============================================================================
# Desinstalar stack de monitoreo
# =============================================================================
log_step "Desinstalando stack de monitoreo"

if helm status monitoring -n monitoring &> /dev/null; then
    log_info "Desinstalando kube-prometheus-stack..."
    helm uninstall monitoring -n monitoring
    log_info "Eliminando namespace monitoring..."
    kubectl delete namespace monitoring --ignore-not-found=true
else
    log_info "Stack de monitoreo no estaba instalado, saltando..."
fi

# =============================================================================
# Eliminar imágenes Docker locales
# =============================================================================
log_step "Eliminando imágenes Docker"

# Apuntar al daemon de Minikube para eliminar las imágenes allí
if minikube status --format='{{.Host}}' 2>/dev/null | grep -q "Running"; then
    eval "$(minikube docker-env)" 2>/dev/null || true
    docker rmi movie-catalog-service:1.0.0 2>/dev/null || log_warn "Imagen movie-catalog-service no encontrada"
    docker rmi ticketing-service:1.0.0 2>/dev/null || log_warn "Imagen ticketing-service no encontrada"
fi

# =============================================================================
# Eliminar cluster (solo con --purge-cluster)
# =============================================================================
if [ "${PURGE_CLUSTER}" = true ]; then
    log_step "Eliminando cluster Minikube completo"
    log_warn "¡Eliminando cluster Minikube! Esto borrará todo el estado del cluster."
    minikube delete
    log_info "Cluster Minikube eliminado"
fi

echo ""
echo -e "${GREEN}╔═══════════════════════════════════════════════╗${NC}"
echo -e "${GREEN}║     ✓ Teardown completado exitosamente         ║${NC}"
echo -e "${GREEN}╚═══════════════════════════════════════════════╝${NC}"

if [ "${PURGE_CLUSTER}" = false ]; then
    echo ""
    log_info "Nota: El cluster Minikube sigue corriendo."
    log_info "Para eliminar el cluster también: bash scripts/teardown.sh --purge-cluster"
fi
