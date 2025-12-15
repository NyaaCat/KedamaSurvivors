pipeline {
    agent any

    tools {
        jdk 'jdk21'
    }

    environment {
        GRADLE_OPTS = '-Dorg.gradle.daemon=false'
    }

    stages {
        stage('Checkout') {
            steps {
                checkout scm
            }
        }

        stage('Build') {
            steps {
                sh './gradlew clean build'
            }
            post {
                always {
                    junit testResults: '**/build/test-results/test/*.xml', allowEmptyResults: true
                }
            }
        }

        stage('Archive') {
            steps {
                archiveArtifacts artifacts: 'build/libs/*-reobf.jar', fingerprint: true
            }
        }
    }

    post {
        cleanup {
            cleanWs()
        }
    }
}
