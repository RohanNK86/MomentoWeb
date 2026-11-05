# Momento API — DevOps pipeline

Backend REST API for **Momento**, the personal productivity platform (tasks, habit streaks,
expenses, dashboard). Written in Kotlin with Ktor; data is kept in memory.

| Component | What it covers | Files |
|---|---|---|
| 1 — CI | Git branching, Gradle build, Jenkins pipeline, JUnit tests, versioned artifacts, GitHub Actions | `server/`, `Jenkinsfile`, `jenkins-docker/`, `.github/workflows/ci.yml` |
| 2 — CD | Ansible role → systemd service, multi-stage Docker image, Kubernetes (Minikube) with ConfigMap, probes, scaling, rollback | `ansible/`, `server/Dockerfile`, `k8s/`, `scripts/rollback-demo.sh` |

```
MomentoWeb/
├── server/                     Momento API (standalone Gradle build, needs only JDK 17)
│   ├── build.gradle.kts        dependencies, test task, fat JAR
│   ├── settings.gradle.kts
│   ├── Dockerfile              multi-stage: Gradle build stage + JRE runtime stage
│   ├── src/main/kotlin/com/momento/server/
│   │   ├── Application.kt      start-up (PORT from env) + Ktor module
│   │   ├── Routes.kt           REST endpoints, /api/health and /api/ready
│   │   ├── MomentoStore.kt     thread-safe in-memory store, streak logic, seed data
│   │   └── Models.kt           serializable data classes
│   └── src/test/kotlin/...     ApplicationTest.kt + MomentoStoreTest.kt (12 JUnit tests)
├── jenkins-docker/Dockerfile   Jenkins LTS (JDK 17) + Gradle 8.10.2
├── Jenkinsfile                 Checkout → Build → Test → Package → archive
├── .github/workflows/ci.yml    GitHub Actions (same test + package)
├── ansible/                    inventory, playbook.yml, roles/appdeploy
├── k8s/                        configmap.yaml, deployment.yaml, service.yaml
└── scripts/rollback-demo.sh
```

## API

| Method and path | Purpose |
|---|---|
| `GET /api/health` | Liveness — 200 `{"status":"UP","version":…,"environment":…}` |
| `GET /api/ready` | Readiness — 200 `READY` after start-up, otherwise 503 |
| `GET, POST /api/tasks` | List tasks (incomplete first) / create (201; blank title → 400) |
| `PATCH /api/tasks/{id}/toggle`, `DELETE /api/tasks/{id}` | Complete/un-complete, delete |
| `GET, POST /api/habits`, `POST /api/habits/{id}/check-in` | List/create habits, check in for today |
| `GET, POST /api/expenses` | List/record expenses (amount must be positive) |
| `GET /api/summary` | Dashboard totals |

---

## Tools required

| Tool | Version | Install on Windows |
|---|---|---|
| Git | any | already installed |
| JDK | 17 (Temurin) | `winget install EclipseAdoptium.Temurin.17.JDK` |
| Gradle | 8.10.2 | download `gradle-8.10.2-bin.zip` from gradle.org, unzip, add `bin` to PATH |
| Docker Desktop | latest, WSL 2 backend | `winget install Docker.DockerDesktop` (then reboot) |
| kubectl | latest | `winget install Kubernetes.kubectl` |
| Minikube | latest | `winget install Kubernetes.minikube` |
| Ansible | ansible-core ≥ 2.17 | inside WSL Ubuntu: `sudo apt update && sudo apt install -y pipx && pipx install --include-deps ansible` |

> Gradle 8.10.2 runs on JDK 17–23. It does **not** run on JDK 24+, so make sure
> `JAVA_HOME` points at JDK 17 when you build locally.

---

## Component 1 — CI with Git, Gradle and Jenkins

### 1. Build and run locally

```bash
cd server
gradle test           # compile and run the 12 tests
gradle buildFatJar    # build/libs/momento-server.jar
java -jar build/libs/momento-server.jar
curl http://localhost:8080/api/health
```

### 2. Git workflow (feature branch + merge commit)

```bash
git checkout -b feature/momento-api
git add server/
git commit -m "Add Momento API server with Ktor and in-memory store"
git add server/src/test
git commit -m "Add JUnit tests for API endpoints and streak logic"
git add Jenkinsfile jenkins-docker/ .github/workflows/ci.yml repo_link.txt
git commit -m "Add Jenkinsfile, Jenkins image and GitHub Actions workflow"
git push -u origin feature/momento-api
# open a PR on GitHub and merge it with "Create a merge commit" (not squash)
git checkout main && git pull
git log --oneline --graph --all
```

### 3. Jenkins in Docker

```bash
docker build -t momento-jenkins jenkins-docker
docker run -d --name jenkins -p 9090:8080 -p 50000:50000 -v jenkins_home:/var/jenkins_home momento-jenkins
docker exec jenkins cat /var/jenkins_home/secrets/initialAdminPassword
```

Open http://localhost:9090 → paste the password → *Install suggested plugins* → create an admin user.

### 4. Jenkins job `momento-pipeline`

New Item → **Pipeline**, then:

| Setting | Value |
|---|---|
| Build Triggers | Poll SCM, schedule `* * * * *` |
| Pipeline → Definition | Pipeline script from SCM |
| SCM | Git |
| Repository URL | `https://github.com/RohanNK86/MomentoWeb.git` |
| Branch Specifier | `*/main` |
| Script Path | `Jenkinsfile` |

Click **Build Now** once (Jenkins reads the `triggers` block on the first run). From then on,
every push to `main` is built automatically within a minute:

```bash
echo "CI: Jenkins polls this repository every minute." >> README.md
git commit -am "Trigger CI: update README"
git push origin main
```

The build page then shows *Started by an SCM change* and the artifact
`server/momento-server-<build>-<sha>.jar`.

---

## Component 2 — Ansible, Docker and Kubernetes

Use branch `feature/deployment` and merge it through a PR, as in Component 1.

### Part A — Ansible (run inside WSL Ubuntu)

systemd must be enabled in WSL. Check `/etc/wsl.conf` contains:

```ini
[boot]
systemd=true
```

(If you had to add it, run `wsl --shutdown` in PowerShell and reopen Ubuntu.)

```bash
cd /mnt/d/MomentoWebApp/MomentoWeb
mkdir -p build
# Download the JAR from the Jenkins build page (Build Artifacts) and save it as build/momento-server.jar
ls -lh build/

cd ansible
ansible-playbook -i inventory playbook.yml --ask-become-pass   # first run: changed, handler runs
ansible-playbook -i inventory playbook.yml --ask-become-pass   # second run: changed=0 (idempotent)
sudo systemctl status momento --no-pager
curl http://localhost:8081/api/health
```

### Part B — Docker image

```bash
docker build -t momento-server:1.0 server
docker images momento-server
docker run -d --name momento-test -p 9000:8080 -e APP_ENV=docker momento-server:1.0
curl http://localhost:9000/api/health
curl http://localhost:9000/api/tasks
docker rm -f momento-test
```

### Part C — Kubernetes (Minikube)

```bash
minikube start --driver=docker --memory=3000 --cpus=2
kubectl get nodes
minikube image load momento-server:1.0

kubectl apply -f k8s/configmap.yaml
kubectl apply -f k8s/deployment.yaml
kubectl apply -f k8s/service.yaml
kubectl rollout status deployment/momento
kubectl get pods -o wide --show-labels
kubectl get svc
kubectl describe svc momento-service | grep -E 'Selector|Endpoints'

minikube service momento-service --url    # keep this terminal open on Windows; use the printed URL
curl <printed-url>/api/health
curl <printed-url>/api/summary
```

### Part D — Scaling

```bash
kubectl scale deployment momento --replicas=5
kubectl get pods -w        # Ctrl+C once all 5 are 1/1
kubectl scale deployment momento --replicas=3
kubectl get pods
```

### Part E — Enhancements: ConfigMap, probes, rollback

```bash
kubectl get configmap momento-config -o yaml
kubectl exec deploy/momento -- printenv PORT APP_ENV
kubectl describe deployment momento
bash scripts/rollback-demo.sh
```

### Clean-up

```bash
kubectl delete -f k8s/
minikube stop
docker stop jenkins
```
