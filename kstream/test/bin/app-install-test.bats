#!/usr/bin/env bats

test_support=test/support
fake_properties="${test_support}/fake-1.app-install.properties"
normal_properties="${test_support}/normal.app-install.properties"

@test "fail if configuration properties file not found" {
  run bin/app-install.sh --config-file i-dont-exist.properties --stop-at config_file

  [ $status -eq 1 ]

  [[ "${lines[0]}" =~ "ERROR" ]]
  [[ "${lines[1]}" =~ "i-dont-exist.properties not found" ]]
}

@test "fail if ssh-keyfile is not set in configuration properties" {
  run bin/app-install.sh --config-file $fake_properties --stop-at config_file

  [ $status -eq 1 ]

  [[ "${lines[0]}" =~ "$fake_properties found" ]]
  [[ "${lines[1]}" =~ "ERROR" ]]
  [[ "${lines[2]}" =~ "ssh-keyfile not defined .. exiting" ]]
}

@test "check only jobs in --start_only are starting - start one job only" {
  run bin/app-install.sh --config-file $normal_properties --start-only dsl --stop-at start_only

  [ $status -eq 0 ]

  [[ "${lines[0]}" =~ "$normal_properties found" ]]
  [[ "${lines[2]}" =~ "DSL based" && "${lines[2]}" =~ "yes" ]]
  [[ "${lines[3]}" =~ "Procedure based" && "${lines[3]}" =~ "no" ]]
}

@test "check only jobs in --start_only are starting - start both jobs" {
  run bin/app-install.sh --config-file $normal_properties --start-only dsl --start-only procedure --stop-at start_only

  [ $status -eq 0 ]

  [[ "${lines[0]}" =~ "$normal_properties found" ]]
  [[ "${lines[2]}" =~ "DSL based" && "${lines[2]}" =~ "yes" ]]
  [[ "${lines[3]}" =~ "Procedure based" && "${lines[3]}" =~ "yes" ]]
}

@test "by default both jobs should start" {
  run bin/app-install.sh --config-file $normal_properties --stop-at start_only

  [ $status -eq 0 ]

  [[ "${lines[0]}" =~ "$normal_properties found" ]]
  [[ "${lines[2]}" =~ "DSL based" && "${lines[2]}" =~ "yes" ]]
  [[ "${lines[3]}" =~ "Procedure based" && "${lines[3]}" =~ "yes" ]]
}

@test "pass if json files for marathon deployment are generated" {
  run bin/app-install.sh --config-file $normal_properties --stop-at marathon_json

  [[ $( basename `ls source/core/deploy.conf` ) == "deploy.conf" ]]
  [[ $( basename `ls source/core/build/dsl/target/universal/*.tgz` ) == "dslpackage-0.1.tgz" ]]
  [[ $( basename `ls source/core/build/proc/target/universal/*.tgz` ) == "procpackage-0.1.tgz" ]]
  [[ $( basename `ls bin/kstream-app-dsl.json` ) == "kstream-app-dsl.json" ]]
  [[ $( basename `ls bin/kstream-app-proc.json` ) == "kstream-app-proc.json" ]]
  [ $status -eq 0 ]
}
