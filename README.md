# yao_streaming

A video platform combining on-demand video (upload, transcode, catalog, watch) with two independent ways to broadcast live — from a dedicated encoder or straight from a browser. Java Spring Boot, MySQL, Docker, Kubernetes (EKS), Terraform-managed AWS infrastructure.

## Prerequisites

- **Docker** + **Docker Compose** — the only requirement. The application, its build tooling, and its test suite all run inside containers; nothing else needs to be installed on the host.

## Local development

```bash
docker compose up -d
```

Starts MySQL, LocalStack, and the application itself.

Verify it's healthy:

```bash
docker compose ps
curl http://localhost:8080/actuator/health
```

Tear down:

```bash
docker compose down
```

## Tests

```bash
docker compose exec app ./gradlew test
```

Integration tests use [Testcontainers](https://testcontainers.com/) to spin up a real, ephemeral MySQL instance per run, independent of the docker-compose MySQL used for manual local development.

## AWS deployment

```bash
cd terraform
terraform init
terraform plan
terraform apply
```

`terraform apply` is a real-money, manual step — always run `plan` and read it first. Once infrastructure is up:

```bash
k8s/aws/deploy.sh
```

Builds and pushes the application's container image, reads live values back out of `terraform output`, applies the Kubernetes manifests, and waits for the rollout. Safe to re-run any time.
