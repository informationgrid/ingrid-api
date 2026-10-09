pipeline {
    agent any
    triggers{ cron( getCronParams() ) }

    tools {
        jdk 'jdk25'
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
                DOCKER_REGISTRY_CREDS = credentials('registry-opencode')
            }
            steps {
                sh './gradlew -Djib.console=plain publishImage'
            }
        }

        stage ('Create SBOMs') {
            when {
                anyOf {
                    branch 'main'
                    buildingTag()
                }
            }
            steps {
                sh "./gradlew cyclonedxDirectBom"
                sh "./gradlew cyclonedxDirectBom -PdevSBOM"
                script {
                    def imageVersion = determineVersion() == 'main' ? 'latest' : determineVersion()
                    def imageToScan = "registry.opencode.de/informationgrid/ingrid-api:${imageVersion}"

                    docker.withRegistry('https://registry.opencode.de', 'registry-opencode') {
                        sh """
                            docker run --rm --pull=always --volumes-from jenkins anchore/syft:latest ${imageToScan} --output cyclonedx-json=${WORKSPACE}/build/reports/sbom-docker-image.json
                        """
                    }
                }
            }
        }

        stage ('Merge SBOMs') {
            when {
                anyOf {
                    branch 'main'
                    buildingTag()
                }
            }
            agent {
                docker {
                    image 'cyclonedx/cyclonedx-cli:latest'
                    // CRITICAL: Overrides the entrypoint so Jenkins can run it as a regular shell agent
                    args '-u root --entrypoint=""'
                    reuseNode true
                }
            }
            steps {
                script {
                    sh """
                        mkdir -p build/reports
                        cyclonedx merge --input-files build/reports/sbom.json build/reports/sbom-docker-image.json --output-file build/reports/sbom-docker.json --output-format json --output-version v1_6 --hierarchical --group de.ingrid --name ingrid-editor --version ${determineVersion()}
                    """
                }
            }
        }

        stage ('Upload SBOM') {
            when {
                anyOf {
                    branch 'main'
                    buildingTag()
                }
            }
            steps {
                script {
                    def parentId = 'cc710f3c-7329-4980-a5da-f4d8f10e7367'
                    def componentName = 'ingrid-api'

                    // Attach SBOM to Docker Image
                    withCredentials([
                        file(credentialsId: 'cosign-private-key', variable: 'COSIGN_KEY_FILE'),
                        string(credentialsId: 'cosign-key-password', variable: 'COSIGN_PASSWORD'),
                        usernamePassword(credentialsId: 'registry-opencode', usernameVariable: 'REG_USER', passwordVariable: 'REG_PASS')
                    ]) {
                        def imageDigest = readFile('build/jib-image.digest').trim()
                        def imageToScan = "registry.opencode.de/informationgrid/${componentName}@${imageDigest}"
                        sh """
                            docker run -u 0:0 --rm \
                              -e COSIGN_PASSWORD \
                              --volumes-from jenkins \
                              ghcr.io/sigstore/cosign/cosign:v3.1.3 attest \
                              --type cyclonedx \
                              --predicate \${WORKSPACE}/build/reports/sbom-docker.json \
                              --key "\$COSIGN_KEY_FILE" \
                              --registry-username "\$REG_USER" \
                              --registry-password "\$REG_PASS" \
                              ${imageToScan}
                        """
                    }

                    // Dependency Track
                    withCredentials([string(credentialsId: 'api-token-dependency-track', variable: 'API_KEY')]) {
                        def baseVersion = determineVersion()
                        def sbomConfigs = [
                            [artifact: 'build/reports/sbom.json',        suffix: '',              tag: 'ingrid_deps_prod'],
                            [artifact: 'build/reports/sbom-dev.json',    suffix: '-dev',          tag: 'ingrid_deps_dev'],
                            [artifact: 'build/reports/sbom-docker.json', suffix: '-docker-image', tag: 'ingrid_deps_docker']
                        ]

                        sbomConfigs.each { cfg ->
                            def props = [group: 'InGrid', parentId: parentId, tags: ['ingrid', cfg.tag]]
                            dependencyTrackPublisher(artifact: cfg.artifact, projectName: componentName, projectVersion: baseVersion + cfg.suffix, synchronous: true, dependencyTrackApiKey: API_KEY, projectProperties: props)
                        }
                    }

                    // NEXUS
                    def repoType = env.TAG_NAME ? "rpm-ingrid-releases" : "rpm-ingrid-snapshots"
                    sh "mv build/reports/sbom.json build/reports/ingrid-api-${determineRpmVersion()}.sbom.json"
                    withCredentials([usernamePassword(credentialsId: '9623a365-d592-47eb-9029-a2de40453f68', passwordVariable: 'PASSWORD', usernameVariable: 'USERNAME')]) {
                        sh '''
                            curl -f --user $USERNAME:$PASSWORD --upload-file build/reports/*.sbom.json https://nexus.informationgrid.eu/repository/''' + repoType + '''/
                        '''
                    }
                }
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
                    sh "sed -i 's/^Version:.*/Version: ${determineRpmVersion()}/' rpm/ingrid-api.spec"
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
                }
            }
        }

        stage('Deploy RPM') {
            when { expression { return shouldBuildDevOrRelease() } }
            steps {
                script {
                    def repoType = env.TAG_NAME ? "rpm-ingrid-releases" : "rpm-ingrid-snapshots"
                    withCredentials([usernamePassword(credentialsId: '9623a365-d592-47eb-9029-a2de40453f68', passwordVariable: 'PASSWORD', usernameVariable: 'USERNAME')]) {
                        sh '''
                            curl -f --user $USERNAME:$PASSWORD --upload-file build/rpms/ingrid/*.rpm https://nexus.informationgrid.eu/repository/''' + repoType + '''/
                        '''
                    }
                }
            }
        }
    }

    post {
        always {
            script {
                if (shouldBuildDevOrRelease()) {
                    junit 'build/test-results/**/*.xml'
                }
            }
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

def determineRpmVersion() {
    return determineVersion().replaceAll('-', '_')
}

def determineRpmReleasePart() {
    if (env.TAG_NAME) {
        if (env.TAG_NAME.startsWith("RPM-")) {
            return env.TAG_NAME.substring(env.TAG_NAME.lastIndexOf("-") + 1)
        }
        return '1'
    } else {
        return 'SNAPSHOT'
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
