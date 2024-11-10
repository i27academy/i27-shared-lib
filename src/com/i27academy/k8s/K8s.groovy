package com.i27academy.k8s

// all the methods 
class K8s {
    def jenkins
    K8s(jenkins) {
        this.jenkins = jenkins
    }
    
    // Method to authenticate to kubernetes cluster
    def auth_login(){
        jenkins.sh """
        echo "********************* Entering into Kubernetes Authentication/Login Method *********************"
        gcloud compute instances list
        echo "********************* Get the K8S Node *********************"
        gcloud container clusters get-credentials i27-cluster --zone us-central1-c --project future-depth-439107-m2
        kubectl get nodes
        """
    }


}



// sudo apt-get update

// sudo apt-get install apt-transport-https ca-certificates gnupg curl -y

// curl https://packages.cloud.google.com/apt/doc/apt-key.gpg | sudo gpg --dearmor -o /usr/share/keyrings/cloud.google.gpg

// echo "deb [signed-by=/usr/share/keyrings/cloud.google.gpg] https://packages.cloud.google.com/apt cloud-sdk main" | sudo tee -a /etc/apt/sources.list.d/google-cloud-sdk.list

// sudo apt-get update && sudo apt-get install google-cloud-cli -y

// gcloud 