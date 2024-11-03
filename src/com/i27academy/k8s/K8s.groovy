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

