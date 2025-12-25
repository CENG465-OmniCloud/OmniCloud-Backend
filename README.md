# OmniCloud - Erasure Coded Multi-Cloud Storage API

**OmniCloud** is a storage orchestration layer designed to prevent vendor lock-in by distributing encrypted file shards across multiple cloud providers using **Reed-Solomon Erasure Coding**.

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
```
---

### 🐳 Step 2: Environment Configuration

We simulate 6 distinct Cloud Providers (AWS, Azure, GCP, etc.) and a Metadata Database using Docker containers.

Run the following command in the project root:

```bash
docker-compose --.env-file ./..env up -d
```

Note: The --env-file flag ensures Docker reads your secrets correctly.


#### 📋 Verify Services Are Running

After starting Docker, verify that all services are running on their designated ports:

| Service | Container Name | Type | Service Port | Console Port |
|---------|----------------|------|--------------|--------------|
| Metadata Database | `omnicloud-metadata` | PostgreSQL | 5435 | - |
| Cloud 1 | `cloud-1-aws-us` | MinIO (AWS US) | 9001 | 9091 |
| Cloud 2 | `cloud-2-aws-eu` | MinIO (AWS EU) | 9002 | 9092 |
| Cloud 3 | `cloud-3-azure` | MinIO (Azure) | 9003 | 9093 |
| Cloud 4 | `cloud-4-gcp` | MinIO (GCP) | 9004 | 9094 |
| Cloud 5 | `cloud-5-oracle` | MinIO (Oracle) | 9005 | 9095 |
| Cloud 6 | `cloud-6-local` | MinIO (Local) | 9006 | 9096 |


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
