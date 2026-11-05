// Jenkins pipeline for the Momento API (server/).
//
//   Checkout -> Build -> Test -> Package -> archive versioned JAR
//
// Triggered by SCM polling every minute: Jenkins runs on a laptop with no public address,
// so GitHub cannot reach it with a webhook. Tests run BEFORE packaging, so a failing test
// stops the build and no artifact is produced. The JAR is named with the Jenkins build
// number and the short Git commit SHA so every artifact traces back to its exact commit.

pipeline {
    agent any

    triggers {
        pollSCM('* * * * *')
    }

    options {
        timestamps()
        disableConcurrentBuilds()
        buildDiscarder(logRotator(numToKeepStr: '20', artifactNumToKeepStr: '10'))
        timeout(time: 30, unit: 'MINUTES')
    }

    environment {
        GRADLE_OPTS = '-Dorg.gradle.daemon=false'
    }

    stages {
        stage('Checkout') {
            steps {
                script {
                    env.SHORT_SHA   = sh(script: 'git rev-parse --short=7 HEAD', returnStdout: true).trim()
                    env.VERSION_TAG = "${env.BUILD_NUMBER}-${env.SHORT_SHA}"
                }
                echo "Building commit ${env.SHORT_SHA} as version ${env.VERSION_TAG}"
            }
        }

        stage('Build') {
            steps {
                dir('server') {
                    sh 'gradle --version'
                    sh 'gradle clean compileKotlin'
                }
            }
        }

        stage('Test') {
            steps {
                dir('server') {
                    sh 'gradle test'
                }
            }
            post {
                always {
                    junit 'server/build/test-results/test/*.xml'
                }
            }
        }

        stage('Package') {
            steps {
                dir('server') {
                    sh 'gradle buildFatJar -x test'
                    sh 'cp build/libs/momento-server.jar "momento-server-${VERSION_TAG}.jar"'
                    sh 'ls -lh momento-server-*.jar'
                }
            }
        }
    }

    post {
        success {
            archiveArtifacts artifacts: 'server/momento-server-*.jar', fingerprint: true
            echo "Artifact: server/momento-server-${env.VERSION_TAG}.jar"
        }
        failure {
            echo 'Build failed - no artifact archived.'
        }
    }
}
