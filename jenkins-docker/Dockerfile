# Jenkins LTS (JDK 17) with Gradle preinstalled, so the pipeline can build the Momento API.
#   docker build -t momento-jenkins jenkins-docker
#   docker run -d --name jenkins -p 9090:8080 -p 50000:50000 -v jenkins_home:/var/jenkins_home momento-jenkins
FROM jenkins/jenkins:lts-jdk17

USER root
ARG GRADLE_VERSION=8.10.2
RUN apt-get update \
 && apt-get install -y --no-install-recommends curl unzip git ca-certificates \
 && curl -fsSL "https://services.gradle.org/distributions/gradle-${GRADLE_VERSION}-bin.zip" -o /tmp/gradle.zip \
 && unzip -q /tmp/gradle.zip -d /opt \
 && ln -s "/opt/gradle-${GRADLE_VERSION}/bin/gradle" /usr/local/bin/gradle \
 && rm -rf /tmp/gradle.zip /var/lib/apt/lists/*
USER jenkins
