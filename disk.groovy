String ManagedByName = ""
String CredID = "sandbox-packer"
def diskExists = true

node('slave')
{
    stage('check disk'){
        while(diskExists){
            try{
                withCredentials([azureServicePrincipal(credentialsId: "${CredID}",
                                            subscriptionIdVariable: 'SUBS_ID',
                                            clientIdVariable: 'CLIENT_ID',
                                            clientSecretVariable: 'CLIENT_SECRET',
                                            tenantIdVariable: 'TENANT_ID')]) {
							sh 'az login --service-principal -u $CLIENT_ID -p $CLIENT_SECRET -t $TENANT_ID ; az account set -s $SUBS_ID'
							sh "az disk show -g ${rgName} -n ${DiskName} --query managedBy > whcichVM.txt"
							sh "sed -i 's/\"//g' whichVM.txt"
							ManagedByName = sh (script: "cat whichVM.txt | cut -d \"/\" -f 9", returnStdout: true).trim()
							println "OUT = ${ManagedByName}"
				}
                if (ManagedByName == "" || ManagedByName == null || ManagedByName.isEmpty())
                {
	                println "Disk ${DiskName} is not attached to any VM"
	                println "Deleting Disk"
	                sh """
	                az disk delete --name ${DiskName}-os --resource-group ${Resource_group_name} --yes
	                """
	                diskExists = false
                }
                else 
                {
                    echo "Disk ${DiskName} is attached to ${ManagedByName}"
                    try{
                        sh """
	                    az vm delete -g ${Resource_group_name} --name ${ManagedByName} --yes
	                    az network nic delete -g ${Resource_group_name} --name ${ManagedByName}
	                    az disk delete --name ${ManagedByName} --resource-group ${Resource_group_name} --yes
	                    az disk delete --name ${DiskName} --resource-group ${Resource_group_name} --yes
	                    """
	                    diskExists = false
                    }
                    catch (Exception e){
                    	println "The VM is currently being use by packer process."
                    } 
                }
            }
        
	        catch(Exception e)
	        {
	            println e
	            diskExists = false
	            println "Disk does not exist"
	        }

	        if (diskExists)
	        {
		        println "${DiskName} is being currently used by packer process. Waiting for 5 mins and trying again."
		        sleep time: 5, unit: 'MINUTES'
		    }
		    else
		    {
		    	break
		    }
        }
        println "Building Image"       
    }
}
