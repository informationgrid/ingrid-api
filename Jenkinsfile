pipeline {
    agent any
    triggers{ cron( getCronParams() ) }

    tools {
        jdk 'jdk21'
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
                    expression { return currentBuild.number > 1 && shouldBuildDockerImage()}
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
                    sh "sed -i 's/^Release:.*/Release: ${determineRpmReleasePart()}/' rpm/ingrid-api.spec"

                    // Prepare build
                    sh "mkdir -p ./build/rpms /root/rpmbuild/SPECS"
                    sh """
                        cp ${WORKSPACE}/rpm/ingrid-api.spec /root/rpmbuild/SPECS/ingrid-api.spec &&
                        rpmbuild -bb /root/rpmbuild/SPECS/ingrid-api.spec
                    """

                    withCredentials([
                        file(credentialsId: 'ingrid-rpm-public', variable: 'RPM_PUBLIC_KEY'),
                        file(credentialsId: 'ingrid-rpm-private', variable: 'RPM_PRIVATE_KEY'),
                        string(credentialsId: 'ingrid-rpm-passphrase', variable: 'RPM_SIGN_PASSPHRASE')
                    ]) {
                        sh 'gpg --batch --import $RPM_PUBLIC_KEY'
                        sh 'gpg --batch --import $RPM_PRIVATE_KEY'
                        sh "mkdir -p ./build/rpms/ingrid"
                        sh "cp -r /root/rpmbuild/RPMS/noarch/* ${WORKSPACE}/build/rpms/ingrid/"
                        sh "expect /rpm-sign.exp ${WORKSPACE}/build/rpms/ingrid/*.rpm"

                        archiveArtifacts artifacts: 'build/rpms/ingrid/ingrid-api-*.rpm', fingerprint: true
                    }

                    withCredentials([
                        file(credentialsId: 'itzbund-ingrid-rpm-public', variable: 'RPM_PUBLIC_KEY'),
                        file(credentialsId: 'itzbund-ingrid-rpm-private', variable: 'RPM_PRIVATE_KEY'),
                        string(credentialsId: 'itzbund-ingrid-rpm-passphrase', variable: 'RPM_SIGN_PASSPHRASE')
                    ]) {
                        sh 'rm -f ~/.gnupg/*.kbx'
                        sh 'rm -f ~/.gnupg/*.gpg'
                        sh 'gpg --batch --import $RPM_PUBLIC_KEY'
                        sh 'gpg --batch --import $RPM_PRIVATE_KEY'
                        sh "mkdir -p ./build/rpms/itzbund"
                        sh "cp -r /root/rpmbuild/RPMS/noarch/* ${WORKSPACE}/build/rpms/itzbund/"
                        sh "expect /rpm-sign.exp ${WORKSPACE}/build/rpms/itzbund/*.rpm"

                        archiveArtifacts artifacts: 'build/rpms/itzbund/ingrid-api-*.rpm', fingerprint: true
                    }
                }
            }
        }

        stage('Deploy RPM') {
            when { expression { return shouldBuildDevOrRelease() } }
            steps {
                script {
                    def repoType = env.TAG_NAME ? "rpm-ingrid-releases" : "rpm-ingrid-snapshots"
                    sh "mv build/reports/bom.json build/reports/ingrid-api-${determineVersion()}.bom.json"
                    archiveArtifacts artifacts: "build/reports/*.bom.json", fingerprint: true

                    withCredentials([usernamePassword(credentialsId: '9623a365-d592-47eb-9029-a2de40453f68', passwordVariable: 'PASSWORD', usernameVariable: 'USERNAME')]) {
                        sh '''
                            curl -f --user $USERNAME:$PASSWORD --upload-file build/rpms/ingrid/*.rpm https://nexus.informationgrid.eu/repository/''' + repoType + '''/
                            curl -f --user $USERNAME:$PASSWORD --upload-file build/reports/*.bom.json https://nexus.informationgrid.eu/repository/''' + repoType + '''/
                        '''
                    }
                    if (repoType == 'rpm-ingrid-releases') {
                        withCredentials([usernamePassword(credentialsId: '9623a365-d592-47eb-9029-a2de40453f68', passwordVariable: 'PASSWORD', usernameVariable: 'USERNAME')]) {
                            sh '''
                                curl -f --user $USERNAME:$PASSWORD --upload-file build/rpms/itzbund/*.rpm https://nexus.informationgrid.eu/repository/rpm-ingrid-itzbund/
                                curl -f --user $USERNAME:$PASSWORD --upload-file build/reports/*.bom.json https://nexus.informationgrid.eu/repository/rpm-ingrid-itzbund/
                            '''
                        }
                        if (env.TAG_NAME && env.TAG_NAME.startsWith("RPM-")) {
                            // No upload to other ITZBund repos
                        } else {
                            withCredentials([usernamePassword(credentialsId: '9623a365-d592-47eb-9029-a2de40453f68', passwordVariable: 'PASSWORD', usernameVariable: 'USERNAME')]) {
                                sh '''
                                    curl -f --user $USERNAME:$PASSWORD --upload-file build/rpms/itzbund/*.rpm https://nexus.informationgrid.eu/repository/rpm-zdm_release/
                                    curl -f --user $USERNAME:$PASSWORD --upload-file build/reports/*.bom.json https://nexus.informationgrid.eu/repository/rpm-zdm_release/
                                '''
                            }
                        }
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
        if (env.TAG_NAME.startsWith("RPM-")) { // e.g. RPM-8.0.0-0.1SNAPSHOT
            def lastDashIndex = env.TAG_NAME.lastIndexOf("-")
            return env.TAG_NAME.substring(4, lastDashIndex)
        }
        return env.TAG_NAME
    } else {
        return env.BRANCH_NAME.replaceAll('/', '_')
    }
}

def determineRpmReleasePart() {
    if (env.TAG_NAME) {
        if (env.TAG_NAME.startsWith("RPM-")) {
            return env.TAG_NAME.substring(env.TAG_NAME.lastIndexOf("-") + 1)
        }
        return '1'
    } else {
        return 'dev'
    }
}

def shouldBuildDevOrRelease() {
    // If no tag is being built OR it is the first build of a tag
    boolean isTag = env.TAG_NAME != null && env.TAG_NAME.trim() != ''
    return !isTag || (isTag && currentBuild.number == 1)
}

def shouldBuildDockerImage() {
    if (env.TAG_NAME && env.TAG_NAME.startsWith("RPM-")) {
        return false
    } else return true
}
