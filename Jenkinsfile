
pipeline {
    agent any

    stages {
        stage('Build') {
            steps {
                echo "Compiling..."
                echo "tool: ${tool name: 'sbt-1.1.0', type: 'org.jvnet.hudson.plugins.SbtPluginBuilder$SbtInstallation'}"
                sh "${tool name: 'sbt-1.1.0', type: 'org.jvnet.hudson.plugins.SbtPluginBuilder$SbtInstallation'}/bin/sbt -Dsbt.log.noformat=true clean compile"
            }
        }
        stage('Unit Test') {
            steps {
                echo "Testing..."
                sh "${tool name: 'sbt-1.1.0', type: 'org.jvnet.hudson.plugins.SbtPluginBuilder$SbtInstallation'}/bin/sbt -Dsbt.log.noformat=true test"
                junit "target/test-reports/*.xml"
            }
        }
        stage('Docker Publish') {
            steps {                
                echo "TODO: Docker Publish stage"
                // Run the Docker tool to build the image
                //script {
                //     sh "${tool name: 'sbt-1.1.0', type: 'org.jvnet.hudson.plugins.SbtPluginBuilder$SbtInstallation'}/bin/sbt -Dsbt.log.noformat=true dockerBuildAndPush"
                //}
            }
        }
    }
}
