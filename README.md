# OmniCloud - Erasure Coded Multi-Cloud Storage API

**OmniCloud** is a next-generation **RESTful Web Service** and **storage orchestration layer** that functions as a "Meta-Cloud" gateway or "Cloud-of-Clouds". Designed to resolve the "Vendor Lock-in paradox" and the systemic vulnerabilities of centralized hyperscale providers, OmniCloud democratizes enterprise-grade **Erasure Coding** technology—typically reserved for giants like Google and Microsoft—via a unified S3-compatible API.

Unlike traditional backup solutions that rely on expensive 1:1 replication, OmniCloud utilizes **Reed-Solomon Erasure Coding** (derived from Rabin’s Information Dispersal Algorithm) and **AES-256 encryption** to mathematically fragment data into secure, meaningless shards. These fragments are distributed across a heterogeneous network of providers (e.g., AWS, Azure, GCP), ensuring **mathematical durability**, **self-healing** capabilities, and **plausible deniability** against vendor-level snooping.

## 🚀 Getting Started

Follow these instructions to set up the **OmniCloud** simulation environment locally.

### 📋 Prerequisites
* **Java 21**
* **Docker** & **Docker Compose** (Must be running)
* **Gradle**
* **IntelliJ IDEA** (Recommended)

---

### 🛠️ Step 1: Environment Configuration

For security reasons, this project uses environment variables to manage sensitive credentials (database passwords, cloud access keys). Since the `.env` file is git-ignored, you must create it manually.

1. Create a file named `.env` in the **root directory** of the project (same level as `docker-compose.yml`).
2. Paste the following configuration into the file:

```properties
# --- DATABASE SETTINGS ---
DB_USER=omniuser
DB_PASSWORD=omnipassword
DB_NAME=omnidb

# --- CLOUD 1 & 2: AWS CREDENTIALS ---
AWS_USER=aws_admin
AWS_PASSWORD=aws_password_secure

# --- CLOUD 3: AZURE CREDENTIALS ---
AZURE_USER=azure_admin
AZURE_PASSWORD=azure_password_secure

# --- CLOUD 4: GCP CREDENTIALS ---
GCP_USER=gcp_admin
GCP_PASSWORD=gcp_password_secure

# --- CLOUD 5: ORACLE CREDENTIALS ---
ORACLE_USER=oracle_admin
ORACLE_PASSWORD=oracle_password_secure

# --- CLOUD 6: LOCAL CLOUD CREDENTIALS ---
LOCAL_USER=local_admin
LOCAL_PASSWORD=local_password_secure

# --- CLOUD 7: IBM CREDENTIALS ---
IBM_USER=ibm_admin
IBM_PASSWORD=ibm_password_secure

# --- CLOUD 8: ALIBABA CREDENTIALS ---
ALIBABA_USER=alibaba_admin
ALIBABA_PASSWORD=alibaba_password_secure
```
---

### 🐳 Step 2: Environment Configuration

We simulate 6 distinct Cloud Providers (AWS, Azure, GCP, etc.) and a Metadata Database using Docker containers.

Run the following command in the project root:

```bash
docker-compose --env-file ./.env up -d
```

Note: The --env-file flag ensures Docker reads your secrets correctly.


#### 📋 Verify Services Are Running

After starting Docker, verify that all services are running on their designated ports:

| Service           | Container Name       | Type            | Region       | Service Port | Console Port |
|-------------------|----------------------|-----------------|--------------|--------------|--------------|
| Metadata Database | `omnicloud-metadata` | PostgreSQL      | -            | 5435         | -            |
| Cloud 1           | `cloud-1-aws-us`     | MinIO (AWS US)  | us-east-1    | 9001         | 9091         |
| Cloud 2           | `cloud-2-aws-eu`     | MinIO (AWS EU)  | eu-central-1 | 9002         | 9092         |
| Cloud 3           | `cloud-3-azure`      | MinIO (Azure)   | west-europe  | 9003         | 9093         |
| Cloud 4           | `cloud-4-gcp`        | MinIO (GCP)     | us-central-1 | 9004         | 9094         |
| Cloud 5           | `cloud-5-oracle`     | MinIO (Oracle)  | uk-london-1  | 9005         | 9095         |
| Cloud 6           | `cloud-6-local`      | MinIO (Local)   | local-server | 9006         | 9096         |
| Cloud 7           | `cloud-7-ibm`        | MinIO (IBM)     | us-south-1   | 9007         | 9097         |
| Cloud 8           | `cloud-8-alibaba`    | MinIO (Alibaba) | cn-hangzhou  | 9008         | 9098         |


### **Basic Docker Commands**

- `docker-compose up -d` - Fresh start
- `docker-compose stop` - Stop containers (keep data)
- `docker-compose start` - Start stopped containers
- `docker-compose ps` - Check status
- `docker-compose down` - Stop and remove everything

---

### ▶️ Step 3: Run the Application (Backend)

Since `application.properties` relies on the environment variables defined in `.env`, you must configure your IDE to read them.

1. **Click on Edit Configurations** (Run menu)
2. **Find the Environment variables field** (You might need to click "Modify Options" to see it)
3. **Copy the content** of your `.env` file and **paste it** into the Environment variables field
    - *(Alternatively, if you use the "EnvFile" plugin, simply attach the `.env` file)*
4. **Run** `OmniCloudApplication`

---

## 📖 Interactive API Documentation (Swagger UI)

This project implements **OpenAPI (Swagger UI)** to visualize and test the API endpoints in real-time.

### Accessing the Dashboard
Once the application is running locally, open the following URL:

> **http://localhost:8080/swagger-ui.html**

For **JSON Docs**:

> **http://localhost:8080/v3/api-docs** 

*(Note: If you configured a different port in `application.properties`, replace `8080` with your port)*

---

## 📡 API Reference

The OmniCloud API is a RESTful Web Service designed to manage multi-cloud storage orchestration. Below is the current status of the API endpoints.

### ✅ Currently Implemented Endpoints
These endpoints are fully operational in the current build.

| Controller      | Method   | Endpoint                          | Description |
|:----------------|:---------|:----------------------------------| :--- |
| **Policies**    | `PUT`    | `/api/v1/policies/geo-fence`      | Update allowed/blocked regions for data placement. |
| **Policies**    | `GET`    | `/api/v1/policies`                | Retrieve global data placement and redundancy policies. |
| **Providers**   | `GET`    | `/api/v1/providers`               | List connected cloud providers (AWS, Azure, etc.). |
| **Providers**   | `POST`   | `/api/v1/providers`               | Add a new cloud provider configuration (Access Keys). |
| **Providers**   | `POST`   | `/api/v1/providers/bulk`          | RBatch register multiple cloud providers (JSON array). |
| **Providers**   | `DELETE` | `/api/v1/providers/{id}`          | Remove a provider from the pool. |
| **Maintenance** | `POST`   | `/api/v1/maintenance/repair/{id}` | Manually trigger a reconstruction job for a file. |
| **File**        | `POST`   | `/api/v1/files/upload`            | Stream upload a file (Encryption + Splitting). |
| **File**        | `GET`    | `/api/v1/files`                   | List all files in the virtual bucket. |
| **Files**       | `GET`    | `/api/v1/files/{id}/metadata`     | View shard distribution and health status. |
| **File**        | `GET`    | `/api/v1/files/{id}/download`     | Stream download (Reconstruction + Decryption). |
| **File**        | `DELETE` | `/api/v1/files/{id}`              | Permanently delete a file and all remote shards. |
| **Auth**        | `POST`   | `/api/v1/auth/register`           | Register a new tenant organization. |
| **Auth**        | `POST`   | `/api/v1/auth/login`              | Authenticate and obtain JWT Bearer token. |
| **Audit**       | `GET`    | `/api/v1/audit/logs`              | Retrieve security access logs for files. |
| **Analytics**   | `GET`    | `/api/v1/analytics/storage`       | Get current storage usage and cost savings report. |
| **Admin**       | `GET`    | `/api/v1/admin/health`            | System-wide health check of all connected clouds. |
