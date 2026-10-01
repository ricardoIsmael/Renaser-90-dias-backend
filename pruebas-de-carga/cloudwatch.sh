#!/usr/bin/env bash
# Baja de CloudWatch, minuto a minuto, las métricas de la EC2 y de la RDS de la RÉPLICA entre dos
# instantes, y las deja en un CSV por recurso. Solo lectura.
#
# Uso: ./cloudwatch.sh <id-instancia-ec2> <id-instancia-rds> <desde ISO> <hasta ISO> <dir-salida>
# (requiere AWS_PROFILE/AWS_REGION; la EC2 con monitoreo detallado para tener datos por minuto)
set -euo pipefail
EC2=${1:?id de la EC2}; RDS=${2:?id de la RDS}; DESDE=${3:?desde}; HASTA=${4:?hasta}; DIR=${5:?dir}
mkdir -p "$DIR"

metrica() { # espacio, dimension, valor, metrica, estadistica
  aws cloudwatch get-metric-statistics --namespace "$1" --dimensions "Name=$2,Value=$3" \
    --metric-name "$4" --statistics "$5" --period 60 --start-time "$DESDE" --end-time "$HASTA" \
    --query "sort_by(Datapoints,&Timestamp)[].[Timestamp,$5]" --output text
}

for m in CPUUtilization:Average CPUUtilization:Maximum CPUCreditBalance:Average NetworkOut:Sum NetworkIn:Sum; do
  metrica AWS/EC2 InstanceId "$EC2" "${m%%:*}" "${m##*:}" | sed "s/^/${m}\t/"
done > "$DIR/cloudwatch-ec2.tsv"

for m in CPUUtilization:Average CPUUtilization:Maximum DatabaseConnections:Maximum FreeableMemory:Minimum \
         ReadIOPS:Average WriteIOPS:Average ReadLatency:Average WriteLatency:Average \
         CPUCreditBalance:Average CPUSurplusCreditBalance:Maximum DiskQueueDepth:Maximum; do
  metrica AWS/RDS DBInstanceIdentifier "$RDS" "${m%%:*}" "${m##*:}" | sed "s/^/${m}\t/"
done > "$DIR/cloudwatch-rds.tsv"

echo "Listo: $DIR/cloudwatch-ec2.tsv y $DIR/cloudwatch-rds.tsv"
