pipeline {
    agent any

    tools {
        jdk 'jdk21'
    }

    options {
        buildDiscarder(logRotator(numToKeepStr: '30'))
    }

    stages {
        stage('initialize') {
            steps {
                script {
                    sh './gradlew clean'
                }
            }
        }
        stage('build') {
            steps {
                sh './gradlew build -x test -x check'
            }
        }
        stage('run tests (unit & intergration)') {
            steps {
                sh './gradlew test'
            }
        }
        stage('deploy') {
            environment {
                DOCKER_REGISTRY_CREDS = credentials('docker-registry-wemove')
            }
            steps {
                sh './gradlew -Djib.console=plain publishImage'
            }
        }
    }

    post {
        always {
            junit 'build/test-results/**/*.xml'
        }
        changed {
            // send Email with Jenkins' default configuration
            script {
                emailext (
                        body: '${DEFAULT_CONTENT}',
                        subject: '${DEFAULT_SUBJECT}',
                        to: '${DEFAULT_RECIPIENTS}')
            }
        }
    }
}
