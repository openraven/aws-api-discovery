# Please read the core readme first
https://github.com/openraven/core

# AWS API Discovery

Service to discover AWS resources using the AWS native API's and publish to Kafka+ElasticSearch

## Prerequisites

* AWS CLI - https://docs.aws.amazon.com/cli/latest/userguide/install-cliv2.html
* Docker Compose - https://docs.docker.com/compose/install
* git - https://git-scm.com/book/en/v2/Getting-Started-Installing-Git

## Clone the repo

```console
$ git clone https://github.com/openraven/aws-api-discovery
```
## Setup AWS permissions and services

You will need read access in the AWS account for the services you'd like to discover.

## Setup kafka, zookeeper and elastic search

    docker-compose -f docker-compose.yml

## Build and run from source

The following will startup the service to discover aws assets and write them to elastic search.

    mvn spring-boot:run -Dspring-boot.run.profiles="default,producer,consumer,ESS,local" 
    
Note: ESS can be replaced with any of the following profiles.


    Accounts
    BACKUP
    EC2
    EFS
    ESS
    FSX
    RDS
    RSH
    S3
    dynamoDb
