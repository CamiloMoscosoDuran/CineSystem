#!/usr/bin/env bash
# =============================================================================
# stress.sh — Script de pruebas de carga para CineSystem
# =============================================================================
# Uso:
#   bash load-testing/stress.sh [opciones]
#
# Opciones:
#   --target    [catalog|ticketing|both]  Servicio a estresar (default: both)
#   --rps       [N]                       Requests por segundo (default: 5)
#   --duration  [N]                       Duración en segundos (default: 60)
#   --host      [IP]                      IP de Minikube (default: auto-detect)
#
# Ejemplos:
#   bash load-testing/stress.sh --target both --rps 10 --duration 120
#   bash load-testing/stress.sh --target ticketing --rps 20 --duration 60
#   bash load-testing/stress.sh --target catalog --rps 5 --duration 300
# =============================================================================

set -euo pipefail

# ==============================
# CONFIGURACIÓN POR DEFECTO
# ==============================
TARGET="both"
REQUESTS_PER_SECOND=5
DURATION_SECONDS=60
MINIKUBE_HOST=""

# Colores
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
RED='\033[0;31m'
NC='\033[0m'

# Parsear argumentos
while [[ $# -gt 0 ]]; do
    case $1 in
        --target)    TARGET="$2"; shift 2 ;;
        --rps)       REQUESTS_PER_SECOND="$2"; shift 2 ;;
        --duration)  DURATION_SECONDS="$2"; shift 2 ;;
        --host)      MINIKUBE_HOST="$2"; shift 2 ;;
        *)           echo "Argumento desconocido: $1"; exit 1 ;;
    esac
done

# Auto-detectar IP de Minikube si no se especificó
if [ -z "${MINIKUBE_HOST}" ]; then
    MINIKUBE_HOST=$(minikube ip 2>/dev/null || echo "127.0.0.1")
fi

CATALOG_URL="http://${MINIKUBE_HOST}:30081"
TICKETING_URL="http://${MINIKUBE_HOST}:30082"

# Delay entre requests para alcanzar el RPS deseado (usa awk, compatible con Git Bash en Windows)
DELAY=$(awk "BEGIN {printf \"%.4f\", 1 / ${REQUESTS_PER_SECOND}}")

echo -e "${CYAN}"
echo "╔══════════════════════════════════════════════════════╗"
echo "║   🎬 CineSystem — Load Testing                       ║"
echo "╠══════════════════════════════════════════════════════╣"
printf "║  Target:    %-40s║\n" "${TARGET}"
printf "║  RPS:       %-40s║\n" "${REQUESTS_PER_SECOND} req/seg"
printf "║  Duración:  %-40s║\n" "${DURATION_SECONDS} segundos"
printf "║  Minikube:  %-40s║\n" "${MINIKUBE_HOST}"
echo "╚══════════════════════════════════════════════════════╝"
echo -e "${NC}"

# Contadores
TOTAL_REQUESTS=0
SUCCESS_COUNT=0
FAIL_COUNT=0

# Función: enviar request al catalog-service
send_catalog_request() {
    local endpoint=$((RANDOM % 4))
    local response_code

    case $endpoint in
        0)
            response_code=$(curl -s -o /dev/null -w "%{http_code}" \
                "${CATALOG_URL}/api/movies" 2>/dev/null || echo "000")
            ;;
        1)
            response_code=$(curl -s -o /dev/null -w "%{http_code}" \
                "${CATALOG_URL}/api/movies/MOV001/availability" 2>/dev/null || echo "000")
            ;;
        2)
            response_code=$(curl -s -o /dev/null -w "%{http_code}" \
                "${CATALOG_URL}/api/movies/MOV002/availability" 2>/dev/null || echo "000")
            ;;
        3)
            response_code=$(curl -s -o /dev/null -w "%{http_code}" \
                "${CATALOG_URL}/metrics" 2>/dev/null || echo "000")
            ;;
    esac

    echo "$response_code"
}

# Función: enviar request al ticketing-service (compras aleatorias)
send_ticketing_request() {
    local movie_id="MOV00$((RANDOM % 4 + 1))"
    local showtime_ids=("SH001" "SH002" "SH003" "SH004" "SH005" "SH006" "SH007" "SH008")
    local showtime_id="${showtime_ids[$((RANDOM % ${#showtime_ids[@]}))]}"
    local quantity=$((RANDOM % 3 + 1))
    local customer="Cliente_$((RANDOM % 1000))"

    local response_code
    response_code=$(curl -s -o /dev/null -w "%{http_code}" \
        -X POST "${TICKETING_URL}/api/tickets/purchase" \
        -H "Content-Type: application/json" \
        -d "{
            \"movieId\": \"${movie_id}\",
            \"showtimeId\": \"${showtime_id}\",
            \"customerName\": \"${customer}\",
            \"customerEmail\": \"${customer}@test.com\",
            \"quantity\": ${quantity},
            \"pricePerSeat\": 12.50
        }" 2>/dev/null || echo "000")

    echo "$response_code"
}

# ==============================
# BUCLE PRINCIPAL DE PRUEBAS
# ==============================
START_TIME=$(date +%s)
END_TIME=$((START_TIME + DURATION_SECONDS))

echo -e "${GREEN}Iniciando prueba de carga... (Ctrl+C para detener)${NC}"
echo ""

while [ "$(date +%s)" -lt "${END_TIME}" ]; do
    LOOP_START=$(date +%s%3N)

    # Enviar requests según el target configurado
    case "${TARGET}" in
        catalog)
            STATUS=$(send_catalog_request)
            ;;
        ticketing)
            STATUS=$(send_ticketing_request)
            ;;
        both)
            if [ $((TOTAL_REQUESTS % 2)) -eq 0 ]; then
                STATUS=$(send_catalog_request)
            else
                STATUS=$(send_ticketing_request)
            fi
            ;;
    esac

    TOTAL_REQUESTS=$((TOTAL_REQUESTS + 1))

    # Clasificar respuesta
    if [[ "${STATUS}" =~ ^2 ]] || [[ "${STATUS}" == "409" ]]; then
        SUCCESS_COUNT=$((SUCCESS_COUNT + 1))
        printf "${GREEN}.${NC}"
    elif [[ "${STATUS}" == "504" ]] || [[ "${STATUS}" == "000" ]]; then
        FAIL_COUNT=$((FAIL_COUNT + 1))
        printf "${RED}T${NC}"  # T = Timeout
    else
        FAIL_COUNT=$((FAIL_COUNT + 1))
        printf "${YELLOW}E${NC}"  # E = Error
    fi

    # Mostrar estadísticas cada 50 requests
    if [ $((TOTAL_REQUESTS % 50)) -eq 0 ]; then
        ELAPSED=$(($(date +%s) - START_TIME))
        REMAINING=$((DURATION_SECONDS - ELAPSED))
        echo ""
        printf "${CYAN}[%ds restantes]${NC} Total: %d | OK: %d | Fail: %d | Tasa éxito: %.1f%%\n" \
            "${REMAINING}" "${TOTAL_REQUESTS}" "${SUCCESS_COUNT}" "${FAIL_COUNT}" \
            "$(echo "scale=1; ${SUCCESS_COUNT} * 100 / ${TOTAL_REQUESTS}" | bc)"
    fi

    # Controlar velocidad para alcanzar RPS
    LOOP_END=$(date +%s%3N)
    ELAPSED_MS=$((LOOP_END - LOOP_START))
    SLEEP_MS=$(echo "scale=3; ${DELAY} * 1000 - ${ELAPSED_MS}" | bc 2>/dev/null || echo "10")
    if (( $(echo "${SLEEP_MS} > 0" | bc -l) )); then
        sleep "${SLEEP_MS}e-3"
    fi
done

# ==============================
# RESUMEN FINAL
# ==============================
ACTUAL_DURATION=$(($(date +%s) - START_TIME))

echo ""
echo ""
echo -e "${CYAN}╔══════════════════════════════════════════════════════╗${NC}"
echo -e "${CYAN}║                RESUMEN DE PRUEBA                    ║${NC}"
echo -e "${CYAN}╠══════════════════════════════════════════════════════╣${NC}"
printf "${CYAN}║${NC}  Total de requests:     %-30s${CYAN}║${NC}\n" "${TOTAL_REQUESTS}"
printf "${CYAN}║${NC}  Exitosos (2xx/409):   %-30s${CYAN}║${NC}\n" "${SUCCESS_COUNT}"
printf "${CYAN}║${NC}  Fallidos:             %-30s${CYAN}║${NC}\n" "${FAIL_COUNT}"
printf "${CYAN}║${NC}  Duración real:        %-30s${CYAN}║${NC}\n" "${ACTUAL_DURATION}s"
if [ "${TOTAL_REQUESTS}" -gt 0 ]; then
    ACTUAL_RPS=$(echo "scale=2; ${TOTAL_REQUESTS} / ${ACTUAL_DURATION}" | bc 2>/dev/null || echo "N/A")
    SUCCESS_RATE=$(echo "scale=1; ${SUCCESS_COUNT} * 100 / ${TOTAL_REQUESTS}" | bc 2>/dev/null || echo "N/A")
    printf "${CYAN}║${NC}  RPS real:             %-30s${CYAN}║${NC}\n" "${ACTUAL_RPS} req/s"
    printf "${CYAN}║${NC}  Tasa de éxito:        %-30s${CYAN}║${NC}\n" "${SUCCESS_RATE}%"
fi
echo -e "${CYAN}╚══════════════════════════════════════════════════════╝${NC}"
