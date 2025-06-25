pipeline {
    agent any

    tools {
        jdk 'jdk21'
    }

    environment {
        RPM_PUBLIC_KEY  = credentials('ingrid-rpm-public')
        RPM_PRIVATE_KEY = credentials('ingrid-rpm-private')
        RPM_SIGN_PASSPHRASE = credentials('ingrid-rpm-passphrase')
        // Determine if we're on a tag and get the version
        GIT_TAG_OUTPUT = sh(script: "git tag --points-at HEAD", returnStdout: true).trim()
        VERSION = sh(script: '''
            if [ -n "${GIT_TAG_OUTPUT}" ]; then
                echo "${GIT_TAG_OUTPUT}"
            else
                git describe --tags --abbrev=0 || echo "0.0.0"
            fi
        ''', returnStdout: true).trim()
        // For non-tag builds, add .dev suffix
        FULL_VERSION = sh(script: '''
            if [ -n "${GIT_TAG_OUTPUT}" ]; then
                echo "${VERSION}"
            else
                echo "${VERSION}.dev"
            fi
        ''', returnStdout: true).trim()
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
                    // Update RPM spec file with the correct version
                    sh "sed -i 's/^Version:.*/Version:                    ${VERSION}/' rpm/ingrid-api.spec"
                    // Update Release field based on whether we're on a tag
                    sh "sed -i 's/^Release:.*/Release:                    ${env.GIT_TAG_OUTPUT ? '1' : 'dev'}/' rpm/ingrid-api.spec"

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
                script {
                    def repoUrl = env.GIT_TAG_OUTPUT ?
                        "https://nexus.informationgrid.eu/repository/rpm-ingrid-releases/" :
                        "https://nexus.informationgrid.eu/repository/rpm-ingrid-snapshots/"

                    echo "Deploying RPM to ${repoUrl} with version ${FULL_VERSION}"

                    withCredentials([usernamePassword(credentialsId: '9623a365-d592-47eb-9029-a2de40453f68', passwordVariable: 'PASSWORD', usernameVariable: 'USERNAME')]) {
                        sh "curl -f --user \$USERNAME:\$PASSWORD --upload-file build/rpms/*.rpm ${repoUrl}"
                    }
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
