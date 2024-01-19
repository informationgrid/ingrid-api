pipeline {
    agent any

    tools {
        jdk 'jdk17'
    }
    
    environment {
        DOCKER_REGISTRY_CREDS = credentials('cafb5bca-ee26-4dc1-9640-545f915030d9')
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
            steps {
                sh './gradlew publishImage'
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
