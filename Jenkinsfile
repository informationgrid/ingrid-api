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
            when {
                allOf {
                    shouldBuildDockerImage()
                    shouldBuildDevOrRelease()
                }
            }
            steps {
                sh './gradlew clean build cyclonedxBom -x test -x check'
            }
        }

        stage ('Base-Image Update') {
            when {
                allOf {
                    shouldBuildDockerImage()
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
                    expression { return shouldBuildDevOrRelease() && shouldBuildDockerImage() }
                    allOf {
                        buildingTag()
                        expression { return currentBuild.number > 1 && shouldBuildDockerImage() }
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
            agent {
                docker {
                    image 'docker-registry.wemove.com/ingrid-rpmbuilder-jdk21-improved'
                    reuseNode true
                }
            }
            steps {
                script {
                    sh "sed -i 's/^Version:.*/Version: ${determineVersion()}/' rpm/ingrid-api.spec"
                    sh "sed -i 's/^Release:.*/Release: ${env.TAG_NAME ? '1' : 'dev'}/' rpm/ingrid-api.spec"

                    // Copy files to expected locations in container
                    sh "mkdir -p ./build/rpms /root/rpmbuild/SPECS"
                    sh "cp ${WORKSPACE}/rpm/ingrid-api.spec /root/rpmbuild/SPECS/ingrid-api.spec"

                    // Build and sign RPM
                    sh """
                        rpmbuild -bb /root/rpmbuild/SPECS/ingrid-api.spec &&
                        gpg --batch --import $RPM_PUBLIC_KEY &&
                        gpg --batch --import $RPM_PRIVATE_KEY &&
                        expect /rpm-sign.exp /root/rpmbuild/RPMS/noarch/*.rpm
                    """

                    // Copy built RPMs back to workspace
                    sh "cp -r /root/rpmbuild/RPMS/noarch/* ${WORKSPACE}/build/rpms/"

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
        if (env.TAG_NAME.startsWith("RPM-")) {
            return env.TAG_NAME.substring(4) // Remove "RPM-" prefix
        }
        return env.TAG_NAME
    } else {
        return env.BRANCH_NAME.replaceAll('/', '_')
    }
}

def shouldBuildDevOrRelease() {
    // If no tag is being built OR it is the first build of a tag
    boolean isTag = env.TAG_NAME != null && env.TAG_NAME.trim() != ''
    return !isTag || (isTag && currentBuild.number == 1)
}

def shouldBuildDockerImage() {
    return !env.TAG_NAME.startsWith("RPM-")
}
