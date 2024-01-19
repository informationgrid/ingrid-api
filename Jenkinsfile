pipeline {
    agent any

    tools {
        jdk 'jdk17'
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
                sh './gradlew build -x test'
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
                sh("REGISTRY_USER=$DOCKER_REGISTRY_CREDS_USR REGISTRY_PWD=$DOCKER_REGISTRY_CREDS_PSW ./gradlew publishImage")
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
