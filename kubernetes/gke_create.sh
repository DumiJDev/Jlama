#!/bin/bash

PROJECT=$1
CLUSTER=$2


REGION=europe-west1-c

function usage() {
cat << EOF
    usage: $0 [GCLOUD_PROJECT_NAME] [GKE_CLUSTER_NAME]

    This script will create a gke cluster to run a distributed bi4j service deployment.
EOF
}


if [[ "$PROJECT" == "" || "$CLUSTER" == "" ]]; then
  usage
  exit 1
fi


# Check if gcloud is installed
if ! command -v gcloud &>/dev/null; then
    echo "Error: gcloud is not installed. Please install it and try again."
    exit 1
fi


HAS_PROJECT=$(gcloud projects list 2>&1 | cut -d " " -f 1 | grep -Fcx $PROJECT)

if [[ "$HAS_PROJECT" == "0" ]]; then
  echo "Unknown project '$PROJECT' list projects with 'gcloud projects list'"
  exit 1
fi


#Set project
echo "Setting project to $PROJECT"
gcloud config set project $PROJECT >/dev/null

echo "Creating cluster..."
gcloud container clusters create $CLUSTER \
    --cluster-version=1.30 \
    --disk-type=hyperdisk-balanced \
    --disk-size=100 \
    --machine-type n4-highcpu-32 \
    --num-nodes 9 \
    --zone $REGION \
    --workload-pool=$PROJECT.svc.id.goog \
    --spot

if [[ $? -ne 0 ]]; then
  echo "Error creating cluster"
  exit 1
fi

echo "Cluster created successfully"

# gcloud storage buckets add-iam-policy-binding $BUCKET     --member "serviceAccount:bi4j-admin@$PROJECT.iam.gserviceaccount.com"     --role "roles/storage.objectViewer"
# gcloud projects add-iam-policy-binding $PROJECT     --member "serviceAccount:bi4j-admin@$PROJECT.iam.gserviceaccount.com"     --role "roles/storage.objectViewer"
# gcloud iam service-accounts add-iam-policy-binding bi4j-admin@$PROJECT.iam.gserviceaccount.com     --role roles/iam.workloadIdentityUser     --member "serviceAccount:$PROJECT.svc.id.goog[bi4j/bi4j-admin]"

gcloud container clusters get-credentials $CLUSTER --zone $REGION

kubectl create namespace bi4j
kubectl create serviceaccount bi4j-admin --namespace bi4j
kubectl annotate serviceaccount bi4j-admin --namespace bi4j iam.gke.io/gcp-service-account=bi4j-admin@$PROJECT.iam.gserviceaccount.com

echo "Now you can deploy the bi4j service with the following command:"
echo "helm install -n bi4j bi4j helm/bi4j -f gke_values.yaml"