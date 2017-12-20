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
  skip
  run bin/app-install.sh --config-file $normal_properties --start-only dsl --stop-at start_only

  [ $status -eq 0 ]
  echo "${lines[0]}"
  echo "${lines[1]}"
  echo "${lines[2]}"

  [[ "${lines[0]}" =~ "$normal_properties found" ]]
  [[ "${lines[2]}" =~ "DSL based" && "${lines[2]}" =~ "yes" ]]
  [[ "${lines[3]}" =~ "Procedure based" && "${lines[3]}" =~ "no" ]]
}

@test "check only jobs in --start_only are starting - start both jobs" {
  skip
  run bin/app-install.sh --config-file $normal_properties --start-only dsl --start-only procedure --stop-at start_only

  [ $status -eq 0 ]

  [[ "${lines[0]}" =~ "$normal_properties found" ]]
  [[ "${lines[2]}" =~ "DSL based" && "${lines[2]}" =~ "yes" ]]
  [[ "${lines[3]}" =~ "Procedure based" && "${lines[3]}" =~ "yes" ]]
}

@test "by default both jobs should start" {
  skip
  run bin/app-install.sh --config-file $normal_properties --stop-at start_only

  [ $status -eq 0 ]

  [[ "${lines[0]}" =~ "$normal_properties found" ]]
  [[ "${lines[2]}" =~ "DSL based" && "${lines[2]}" =~ "yes" ]]
  [[ "${lines[3]}" =~ "Procedure based" && "${lines[3]}" =~ "yes" ]]
}
