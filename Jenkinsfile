pipeline {
    agent any
    triggers{ cron( getCronParams() ) }

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
        stage('Build Image') {
            when { expression { return shouldBuildDevOrRelease() } }
            steps {
                sh './gradlew clean build cyclonedxBom -x test -x check'
            }
        }

        stage ('Base-Image Update') {
            when {
                allOf {
                    buildingTag()
                    expression { return currentBuild.number > 1 }
                }
            }
            steps {
                sh './gradlew --no-daemon -Djib.console=plain build -x test -x check'
            }
        }

        stage('Deploy Image') {
            // do not run when building a release from a branch-Jenkins-Job
            // In Jenkins there's a special Tag-Job, that handles the release
            when {
                anyOf {
                    expression { return shouldBuildDevOrRelease() }
                    allOf {
                        buildingTag()
                        expression { return currentBuild.number > 1 }
                    }
                }
            }
            environment {
                DOCKER_REGISTRY_CREDS = credentials('docker-registry-wemove')
            }
            steps {
                sh './gradlew -Djib.console=plain publishImage'
            }
        }

        stage('Tests') {
            when { expression { return shouldBuildDevOrRelease() } }
            steps {
                sh './gradlew test'
            }
        }

        stage('Build RPM') {
            when { expression { return shouldBuildDevOrRelease() } }
            steps {
                script {
                    sh "sed -i 's/^Version:.*/Version: ${determineVersion()}/' rpm/ingrid-api.spec"
                    sh "sed -i 's/^Release:.*/Release: ${env.TAG_NAME ? '1' : 'dev'}/' rpm/ingrid-api.spec"

                    def containerId = sh(script: "docker run -d -e RPM_SIGN_PASSPHRASE=\$RPM_SIGN_PASSPHRASE --entrypoint=\"\" docker-registry.wemove.com/ingrid-rpmbuilder-jdk21-improved tail -f /dev/null", returnStdout: true).trim()

                    try {

                        sh """
                            docker cp build/distributions ${containerId}:/files &&
                            docker cp rpm/ingrid-api.spec ${containerId}:/root/rpmbuild/SPECS/ingrid-api.spec &&
                            docker cp rpm/. ${containerId}:/rpm &&
                            docker cp \$RPM_PUBLIC_KEY ${containerId}:/public.key &&
                            docker cp \$RPM_PRIVATE_KEY ${containerId}:/private.key &&
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
            when { expression { return shouldBuildDevOrRelease() } }
            steps {
                script {
                    def repoType = env.TAG_NAME ? "rpm-ingrid-releases" : "rpm-ingrid-snapshots"
                    sh "mv build/reports/bom.json build/reports/ingrid-api-${determineVersion()}.bom.json"
                    // Test comment

                    withCredentials([usernamePassword(credentialsId: '9623a365-d592-47eb-9029-a2de40453f68', passwordVariable: 'PASSWORD', usernameVariable: 'USERNAME')]) {
                        sh '''
                            curl -f --user $USERNAME:$PASSWORD --upload-file build/rpms/*.rpm https://nexus.informationgrid.eu/repository/''' + repoType + '''/
                            curl -f --user $USERNAME:$PASSWORD --upload-file build/reports/*.bom.json https://nexus.informationgrid.eu/repository/''' + repoType + '''/
                        '''
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

def getCronParams() {
    String tagTimestamp = env.TAG_TIMESTAMP
    long diffInDays = 0
    if (tagTimestamp != null) {
        long diff = "${currentBuild.startTimeInMillis}".toLong() - "${tagTimestamp}".toLong()
        diffInDays = diff / (1000 * 60 * 60 * 24)
        echo "Days since release: ${diffInDays}"
    }

    def versionMatcher = /\d\.\d\.\d(.\d)?/
    if( env.TAG_NAME ==~ versionMatcher && diffInDays < 180) {
        // every Sunday between midnight and 6am
        return 'H H(0-6) * * 0'
    }
    else {
        return ''
    }
}

def determineVersion() {
    if (env.TAG_NAME) {
        return env.TAG_NAME
    } else {
        return env.BRANCH_NAME.replaceAll('/', '_')
    }
}

def shouldBuildDevOrRelease() {
    // If no tag is being built OR it is the first build of a tag
    return !buildingTag() || (buildingTag() && currentBuild.number == 1)
}
