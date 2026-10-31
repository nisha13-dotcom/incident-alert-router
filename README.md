# 🚨 Incident Alert Router

### Event-Driven Incident Management System using Java, Spring Boot and AWS

An event-driven, cloud-based incident management system that receives application incidents through REST APIs, stores them in Amazon DynamoDB, processes them asynchronously using Amazon SQS and AWS Lambda, and automatically escalates unacknowledged incidents using Amazon EventBridge Scheduler.

The system is designed around reliability, duplicate prevention, asynchronous processing, automatic retries, failure handling, and cloud monitoring.

---

## 📌 Table of Contents

1. Project Overview
2. Problem Statement
3. Key Features
4. System Architecture
5. Technology Stack
6. How the System Works
7. Database Design
8. AWS Infrastructure Setup
9. Local Development Setup
10. AWS Configuration
11. Running the Application
12. Testing the APIs
13. Lambda Deployment
14. Monitoring and Logging
15. CI/CD Pipeline
16. Docker
17. Problems Faced and Solutions
18. Security Considerations
19. Future Improvements
20. Author

---

## 1. Project Overview

In real-world software systems, application failures can happen at any time. A payment API may stop responding, a database may become unavailable, or a critical backend service may experience errors.

If incidents are not detected, processed, and acknowledged quickly, they can affect users and business operations.

The Incident Alert Router addresses this problem by providing a centralized incident management workflow.

It uses an asynchronous, serverless architecture to separate incident creation from incident processing.

**Example:**

A payment service experiences repeated failures.

1. The application creates an incident through a REST API.
2. Spring Boot validates the request.
3. The system generates a unique incident fingerprint.
4. DynamoDB stores the incident and fingerprint atomically.
5. Amazon SQS receives an incident message.
6. AWS Lambda automatically processes the message.
7. DynamoDB records the processing result.
8. EventBridge Scheduler periodically checks for incidents that have not been acknowledged.
9. Incidents that remain unacknowledged beyond five minutes are automatically escalated.

---

## 2. Problem Statement

Traditional incident handling can involve manual monitoring, delayed notifications, duplicate incident records, and unreliable background processing.

This project addresses the following challenges:

| Problem | Solution |
|---|---|
| Duplicate incidents | Fingerprint-based duplicate prevention |
| Slow synchronous processing | Amazon SQS asynchronous messaging |
| Failed message processing | SQS retry mechanism and Dead-Letter Queue |
| Manual incident escalation | EventBridge Scheduler and Lambda |
| Difficult debugging | Amazon CloudWatch logs and metrics |
| Application and cloud coupling | Event-driven architecture |

---

## 3. Key Features

- RESTful APIs using Spring Boot.
- Incident creation and retrieval.
- Severity and service-based filtering.
- Fingerprint-based duplicate prevention.
- Atomic DynamoDB transaction for incident and fingerprint creation.
- Asynchronous message processing using Amazon SQS.
- Automatic SQS-to-Lambda triggering.
- Lambda-based incident processing.
- Separate business status and processing status.
- Failed-message retry handling.
- Dead-Letter Queue (DLQ) for repeatedly failed messages.
- Incident acknowledgement.
- Automatic escalation after five minutes without acknowledgement.
- Scheduled escalation checks using EventBridge Scheduler.
- CloudWatch monitoring and logging.
- Docker configuration.
- GitHub Actions CI workflow.

---

## 4. System Architecture

```text
                  Client / Developer
                          |
                          v
                  REST API Request
                          |
                          v
                Spring Boot Application
                          |
              +-----------+-----------+
              |                       |
              v                       v
       Request Validation       Fingerprint Generation
                                      |
                                      v
                              DynamoDB Transaction
                                      |
                          +-----------+-----------+
                          |                       |
                          v                       v
                   IncidentAlerts       FingerprintIndex
                          |
                          v
                    Amazon SQS
                   incident-alerts
                          |
                          v
                  AWS Lambda Worker
                          |
                          v
                     DynamoDB
                 Update Processing Status

             Failed message processing
                          |
                          v
                    SQS Retry
                          |
                          v
                       DLQ
               incident-alerts-dlq


              EventBridge Scheduler
                   Every 1 minute
                          |
                          v
               Escalation Lambda Worker
                          |
                          v
                Incident Escalation Check
                          |
                          v
                     DynamoDB
                          |
                          v
                Status = ESCALATED


               Amazon CloudWatch
             Logs / Metrics / Dashboard
```

### Architecture Components

**Spring Boot Application**

Receives incident requests, validates input, creates incidents, prevents duplicates, and publishes messages to SQS.

**Amazon DynamoDB**

Stores incident information and fingerprint records.

**Amazon SQS**

Provides reliable asynchronous communication between the application and the processing worker.

**AWS Lambda – SQS Worker**

Automatically processes messages received from the SQS queue and updates incident processing status.

**Amazon SQS Dead-Letter Queue**

Stores messages that repeatedly fail processing after the configured retry limit.

**Amazon EventBridge Scheduler**

Invokes the escalation Lambda every minute.

**AWS Lambda – Escalation Worker**

Checks for eligible unacknowledged incidents and updates their status to ESCALATED.

**Amazon CloudWatch**

Provides logs and metrics for monitoring the Lambda workers.

---

## 5. Technology Stack

| Category | Technology |
|---|---|
| Programming Language | Java 21 |
| Backend Framework | Spring Boot |
| Build Tool | Maven |
| Database | Amazon DynamoDB |
| Messaging | Amazon SQS |
| Serverless Processing | AWS Lambda |
| Scheduling | Amazon EventBridge Scheduler |
| Monitoring | Amazon CloudWatch |
| Containerization | Docker |
| CI/CD | GitHub Actions |
| Version Control | Git and GitHub |
| IDE | Visual Studio Code |
| Operating System Used | Windows |

---

## 6. How the System Works

### Step 1: Incident Creation

A client sends a POST request to:

`POST /api/incidents`

Example request:

```json
{
  "title": "Payment API response failure",
  "service": "payment-service",
  "severity": "HIGH"
}
```

Spring Boot validates the request and creates an incident.

### Step 2: Duplicate Prevention

The application generates an incident fingerprint.

The fingerprint helps identify duplicate incidents.

A DynamoDB transaction creates the incident record and claims the fingerprint together. This prevents two concurrent requests from independently creating the same fingerprint record.

### Step 3: Asynchronous Processing

After successful creation, the application publishes an incident message to Amazon SQS.

The API does not need to wait for the Lambda worker to finish processing.

### Step 4: Lambda Processing

Amazon SQS automatically triggers the SQS processing Lambda.

The Lambda worker:

1. Receives the message.
2. Validates the message JSON.
3. Retrieves the incident from DynamoDB.
4. Updates `processingStatus` to `PROCESSED`.
5. Logs successful processing.

### Step 5: Retry and Dead-Letter Queue

If message processing repeatedly fails, SQS retries delivery.

The configured redrive policy moves messages to the Dead-Letter Queue after three unsuccessful receives.

Configuration used:

- Main queue: `incident-alerts`
- Dead-Letter Queue: `incident-alerts-dlq`
- Visibility timeout: 60 seconds
- Maximum receives: 3

### Step 6: Incident Escalation

The EventBridge Scheduler invokes the escalation Lambda every minute.

The escalation worker checks incidents that:

- Are still in NEW status.
- Have not been acknowledged.
- Have exceeded the five-minute escalation threshold.

Eligible incidents are updated to:

`status = ESCALATED`

The escalation timestamp is stored in `escalatedAt`.

---

## 7. Database Design

### Table 1: IncidentAlerts

Stores incident information.

| Attribute | Description |
|---|---|
| id | Unique incident identifier |
| createdAt | Incident creation timestamp |
| title | Incident title |
| service | Affected service |
| severity | Incident severity |
| status | Business lifecycle status |
| processingStatus | Asynchronous processing status |
| fingerprint | Duplicate identification value |
| escalatedAt | Escalation timestamp |

### Table 2: IncidentFingerprintIndex

Stores fingerprint claims used for duplicate prevention.

The transaction ensures that the incident record and corresponding fingerprint claim are created together.

### Incident Status vs Processing Status

The application uses two separate status fields.

**Business lifecycle status:**

- NEW
- ACKNOWLEDGED
- ESCALATED

**Message processing status:**

- PENDING
- PROCESSED
- DELIVERY_FAILED

These statuses represent different things.

For example, an incident can have:

```json
{
  "status": "ESCALATED",
  "processingStatus": "PROCESSED"
}
```

This means the SQS processing succeeded, but the incident was not acknowledged within the escalation period.

---

## 8. AWS Infrastructure Setup

The following AWS resources are required.

### 8.1 Create an AWS Account

1. Visit https://aws.amazon.com/
2. Create an AWS account.
3. Complete account verification.
4. Enable multi-factor authentication (MFA).
5. Select the AWS region used by this project.

The deployment documented in this project used:

`us-east-1`

### 8.2 Configure IAM

Create an IAM identity for development with only the permissions required by the project.

The application and Lambda workers require appropriate access to:

- DynamoDB.
- SQS.
- Lambda logging through CloudWatch.

The deployment and infrastructure-management identity also needs the relevant deployment permissions.

Avoid using root account credentials for development.

### 8.3 Create DynamoDB Tables

Create:

- `IncidentAlerts`
- `IncidentFingerprintIndex`

Use the key schema expected by the application.

### 8.4 Create SQS Queues

Create:

- `incident-alerts`
- `incident-alerts-dlq`

Configure the main queue's redrive policy to use the DLQ.

### 8.5 Create Lambda Functions

Create the Java 21 Lambda functions:

- `IncidentRouter-SQS-Worker`
- `IncidentRouter-Escalation-Worker`

Configure their execution roles and required DynamoDB and CloudWatch permissions.

### 8.6 Configure EventBridge Scheduler

Create a recurring schedule that invokes the escalation Lambda every minute.

Ensure the scheduler has permission to invoke the Lambda function.

---

## 9. Local Development Setup

### Prerequisites

Install the following tools before running the project.

| Tool | Purpose |
|---|---|
| Java 21 JDK | Run the Spring Boot application |
| Git | Version control |
| Maven Wrapper | Build the project |
| Visual Studio Code | Development environment |
| AWS CLI | Configure AWS access |
| Docker Desktop | Run containerized builds |
| AWS Account | Access AWS services |

### Step 1: Install Java 21

Download Java 21 from:

https://adoptium.net/

Install the JDK and configure the Java environment variable if required.

Verify installation:

```powershell
java -version
```

Expected version:

```text
21
```

Also verify:

```powershell
javac -version
```

### Step 2: Install Git

Download:

https://git-scm.com/downloads

Verify:

```powershell
git --version
```

### Step 3: Clone the Repository

```powershell
git clone https://github.com/nisha13-dotcom/incident-alert-router.git
```

Move into the project folder:

```powershell
cd incident-alert-router
```

### Step 4: Verify Maven Wrapper

This project includes Maven Wrapper files.

On Windows PowerShell:

```powershell
.\mvnw.cmd -version
```

A separate Maven installation is not required when using the included wrapper.

---

## 10. AWS Configuration

### Step 1: Install AWS CLI

Download:

https://docs.aws.amazon.com/cli/latest/userguide/getting-started-install.html

Verify:

```powershell
aws --version
```

### Step 2: Configure an AWS CLI Profile

Use an IAM identity with the required development permissions.

```powershell
aws configure --profile incident-router-dev
```

Enter the credentials when prompted.

Configure:

```text
Default region name: us-east-1
Default output format: json
```

Never commit AWS access keys or secret credentials to GitHub.

### Step 3: Select the Profile

In PowerShell:

```powershell
$env:AWS_PROFILE="incident-router-dev"
$env:AWS_REGION="us-east-1"
```

Verify the identity:

```powershell
aws sts get-caller-identity --profile incident-router-dev
```

This confirms which AWS identity the CLI is using.

---

## 11. Running the Application

Open PowerShell in the project root.

Set the AWS environment:

```powershell
$env:AWS_PROFILE="incident-router-dev"
$env:AWS_REGION="us-east-1"
```

Start Spring Boot:

```powershell
.\mvnw.cmd spring-boot:run
```

The application starts on:

`http://localhost:8080`

### DynamoDB Health Check

```http
GET http://localhost:8080/api/aws/dynamodb-health
```

This endpoint checks DynamoDB connectivity.

---

## 12. Testing the APIs

### Create an Incident

```http
POST http://localhost:8080/api/incidents
Content-Type: application/json
```

Request:

```json
{
  "title": "Payment API response failure",
  "service": "payment-service",
  "severity": "HIGH"
}
```

### Retrieve Incidents

```http
GET http://localhost:8080/api/incidents
```

### Filter Incidents

The application supports filtering by severity and service through its implemented API endpoints.

Check `IncidentController.java` for the exact query parameters.

### Test Using PowerShell

```powershell
$body = @{
    title = "Payment API response failure"
    service = "payment-service"
    severity = "HIGH"
} | ConvertTo-Json

Invoke-RestMethod `
    -Uri "http://localhost:8080/api/incidents" `
    -Method Post `
    -ContentType "application/json" `
    -Body $body
```

---

## 13. Lambda Deployment

The SQS worker is packaged as a Java Lambda deployment artifact.

Build the project:

```powershell
.\mvnw.cmd clean package -DskipTests
```

The Lambda deployment artifact used in this project:

```text
target/incident-alert-router-0.0.1-SNAPSHOT-lambda.jar
```

The deployed SQS worker uses the handler:

```text
com.incidentrouter.IncidentSqsLambdaHandler::handleRequest
```

Upload the packaged JAR to the configured deployment bucket and update the Lambda function code.

The escalation worker is deployed separately and uses its own Lambda handler.

Keep the Lambda runtime, handler configuration, and packaged dependencies consistent with the application build.

---

## 14. Monitoring and Logging

Amazon CloudWatch is used to monitor Lambda execution.

The project uses the following Lambda functions:

- `IncidentRouter-SQS-Worker`
- `IncidentRouter-Escalation-Worker`

Relevant CloudWatch metrics include:

- Invocations
- Errors
- Duration
- Throttles
- Concurrent executions

CloudWatch log groups:

```text
/aws/lambda/IncidentRouter-SQS-Worker
/aws/lambda/IncidentRouter-Escalation-Worker
```

The monitoring dashboard:

`IncidentRouter-Monitoring`

The dashboard includes invocation and error monitoring for the two Lambda workers.

CloudWatch alarms can be configured to notify developers through Amazon SNS when important error thresholds are reached.

---

## 15. CI/CD Pipeline

The project includes a GitHub Actions workflow:

```text
.github/workflows/ci.yml
```

The workflow uses:

1. GitHub Actions.
2. Repository checkout.
3. Java 21 setup.
4. Maven dependency caching.
5. Maven package build.

The current build command is:

```bash
./mvnw -B clean package -DskipTests
```

The workflow provides automated build validation when changes are pushed to GitHub.

Future CI/CD improvements include automated test execution, artifact publishing, and controlled AWS deployment.

---

## 16. Docker

The repository includes:

```text
Dockerfile
.dockerignore
```

Docker is intended to make application packaging and execution more consistent across environments.

Build and run the container after verifying the Dockerfile configuration:

```powershell
docker build -t incident-alert-router .
```

```powershell
docker run -p 8080:8080 incident-alert-router
```

AWS credentials should be supplied securely at runtime when required. Never bake credentials into the Docker image.

---

## 17. Problems Faced and Solutions

Building this project involved solving several real development and cloud integration problems.

### Problem 1: AWS Credentials Not Recognized by Spring Boot

**Issue:** The application could not consistently access AWS resources.

**Cause:** The application was not using the intended AWS CLI profile.

**Solution:**

Set the environment variables before starting Spring Boot:

```powershell
$env:AWS_PROFILE="incident-router-dev"
$env:AWS_REGION="us-east-1"
```

This ensured that the application used the intended development identity.

### Problem 2: DynamoDB Permission Errors

**Issue:** Some AWS CLI operations returned AccessDenied errors.

**Cause:** The IAM identity did not have every requested permission.

**Solution:** Verify the required IAM permissions and use specific operations such as `describe-table` and `get-item` when those are permitted. Do not assume that permission to read an item also grants permission to list all tables.

### Problem 3: Duplicate Incident Creation

**Issue:** Repeated requests could create duplicate incident records.

**Solution:** Implement fingerprint-based duplicate prevention using DynamoDB transactions.

### Problem 4: Reliable Asynchronous Processing

**Issue:** Incident creation and background processing should not depend on the same synchronous request.

**Solution:** Introduce Amazon SQS between Spring Boot and the Lambda processing worker.

### Problem 5: Failed SQS Messages

**Issue:** A malformed message can fail during processing.

**Solution:** Configure SQS retry handling and a Dead-Letter Queue.

The DLQ behavior was tested using a deliberately malformed JSON message. The message was retried and eventually moved to the DLQ.

### Problem 6: Lambda Not Processing Messages Automatically

**Issue:** Creating a Lambda function alone does not automatically connect it to an SQS queue.

**Solution:** Configure the SQS event source mapping and the required Lambda execution permissions.

Successful processing was verified through CloudWatch logs and DynamoDB records.

### Problem 7: Automatic Escalation

**Issue:** Incidents that remain unacknowledged should not stay NEW indefinitely.

**Solution:** Implement a scheduled escalation worker using EventBridge Scheduler and Lambda.

The escalation workflow was verified by checking the updated incident status and `escalatedAt` timestamp in DynamoDB.

### Problem 8: GitHub Authentication Failure

**Issue:** Git push initially failed because GitHub no longer accepts account passwords for HTTPS Git authentication.

**Solution:** Authenticate through GitHub's supported Visual Studio Code authorization flow and push the repository again.

### Problem 9: Git Repository Root Was Incorrect

**Issue:** Git initially detected files outside the intended project directory.

**Solution:** Verify the repository root with:

```powershell
git rev-parse --show-toplevel
```

Initialize and manage Git from the actual project root.

---

## 18. Security Considerations

Security is important when working with cloud infrastructure.

This project follows these practices:

- Use IAM identities instead of AWS root credentials for development.
- Use MFA for AWS account protection.
- Do not upload AWS access keys or secret credentials.
- Keep `.aws/`, `.env`, credential JSON files, and private key files out of Git.
- Use `.gitignore` to exclude sensitive files.
- Avoid hardcoding AWS account IDs and credentials in source code.
- Use appropriate IAM permissions for Lambda functions.
- Keep secrets out of Docker images and GitHub repositories.

For production deployment, use managed secrets and secure CI/CD authentication such as GitHub Actions OIDC.

---

## 19. Future Improvements

The following improvements are planned to make the project more production-ready.

| Improvement | Purpose |
|---|---|
| Terraform | Infrastructure as Code for repeatable AWS deployments |
| GitHub Actions deployment | Automated deployment after successful builds |
| GitHub Actions OIDC | Secure AWS authentication without long-lived access keys |
| SNS notifications | Email notifications for escalated incidents |
| Automated integration tests | Verify AWS service interactions |
| API documentation with Swagger | Interactive API documentation |
| Authentication and authorization | Secure incident management APIs |
| React dashboard | Visual incident monitoring interface |
| Improved retry and recovery | More resilient message processing |
| Distributed tracing | Better debugging across services |

### Terraform Infrastructure as Code

Terraform is planned to manage the AWS resources, including:

- DynamoDB tables.
- SQS queues and DLQ.
- Lambda functions.
- IAM roles and policies.
- EventBridge Scheduler.
- CloudWatch log groups and alarms.

This will make the infrastructure easier to reproduce and maintain.

---

## 20. Project Validation

The following core workflows have been implemented and verified:

- [x] Spring Boot REST API
- [x] DynamoDB connectivity
- [x] Incident creation
- [x] Fingerprint-based duplicate prevention
- [x] DynamoDB transaction
- [x] SQS message publishing
- [x] Automatic SQS-to-Lambda triggering
- [x] Lambda processing and DynamoDB update
- [x] SQS retry and DLQ behavior
- [x] EventBridge scheduled escalation
- [x] Escalation status and timestamp verification
- [x] CloudWatch log verification
- [x] GitHub repository upload
- [x] GitHub Actions workflow file

Further work:

- [ ] Complete Docker build and runtime validation
- [ ] Expand automated CI test execution
- [ ] Implement Terraform
- [ ] Implement automated AWS deployment
- [ ] Add SNS email notification testing

---

## 21. Author

**Nisha**

Computer Science and Engineering

GitHub: https://github.com/nisha13-dotcom

Project Repository: https://github.com/nisha13-dotcom/incident-alert-router

---

### Final Note

This project demonstrates practical experience with Java backend development, AWS cloud services, event-driven architecture, asynchronous processing, serverless computing, failure handling, and cloud monitoring.

It is an ongoing engineering project, with additional infrastructure automation and deployment improvements planned.
