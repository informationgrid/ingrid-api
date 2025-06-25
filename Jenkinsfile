pipeline {
    agent any

    tools {
        jdk 'jdk21'
    }

    environment {
        RPM_PUBLIC_KEY  = credentials('ingrid-rpm-public')
        RPM_PRIVATE_KEY = credentials('ingrid-rpm-private')
        RPM_SIGN_PASSPHRASE = credentials('ingrid-rpm-passphrase')
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
        stage('run tests (unit & integration)') {
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

        stage('Build RPM') {
            steps {
                echo 'Starting to build RPM package'

                script {

                    def containerId = sh(script: "docker run -d -e RPM_SIGN_PASSPHRASE=$RPM_SIGN_PASSPHRASE --entrypoint=\"\" docker-registry.wemove.com/ingrid-rpmbuilder-jdk21-improved tail -f /dev/null", returnStdout: true).trim()

                    try {
                        sh "docker cp build/distributions ${containerId}:/files"
                        sh "docker cp rpm/ingrid-api.spec ${containerId}:/root/rpmbuild/SPECS/ingrid-api.spec"
                        sh "docker cp rpm/. ${containerId}:/rpm"
                        sh "docker cp $RPM_PUBLIC_KEY ${containerId}:/public.key"
                        sh "docker cp $RPM_PRIVATE_KEY ${containerId}:/private.key"

                        sh """
                            docker exec ${containerId} bash -c "
                            rpmbuild -bb /root/rpmbuild/SPECS/ingrid-api.spec &&
                            gpg --batch --import public.key &&
                            gpg --batch --import private.key &&
                            expect /rpm-sign.exp /root/rpmbuild/RPMS/noarch/*.rpm
                            "
                        """

                        sh "docker cp ${containerId}:/root/rpmbuild/RPMS/noarch ./build/rpms"

                    } finally {
                        sh "docker rm -f ${containerId}"
                    }

                    archiveArtifacts artifacts: 'build/rpms/ingrid-api-*.rpm', fingerprint: true
                }
            }
        }

        stage('Deploy RPM') {
            steps {
                withCredentials([usernamePassword(credentialsId: '9623a365-d592-47eb-9029-a2de40453f68', passwordVariable: 'PASSWORD', usernameVariable: 'USERNAME')]) {
                    sh 'curl -f --user $USERNAME:$PASSWORD --upload-file build/rpms/*.rpm https://nexus.informationgrid.eu/repository/rpm-ingrid/'
                }
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
