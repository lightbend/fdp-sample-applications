#!/usr/bin/env bats

test_support=test/support
fake_properties="${test_support}/fake.app-install.properties"
fake1_properties="${test_support}/fake1.app-install.properties"
normal_properties="${test_support}/normal.app-install.properties"

@test "fail if configuration properties file not found" {
  run bin/app-install.sh --config-file i-dont-exist.properties --stop-at config_file

  [ $status -eq 1 ]

  [[ "${lines[0]}" =~ "ERROR" ]]
  [[ "${lines[1]}" =~ "i-dont-exist.properties not found" ]]
}

@test "fail if s3-bucket-url is not set in configuration properties" {
  run bin/app-install.sh --config-file $fake_properties --stop-at config_file

  [ $status -eq 1 ]

  [[ "${lines[0]}" =~ "$fake_properties found" ]]
  [[ "${lines[1]}" =~ "ERROR" ]]
  [[ "${lines[2]}" =~ "s3-bucket-url requires a non-empty argument" ]]
}

@test "fail if docker-username set to empty in configuration properties" {
  run bin/app-install.sh --config-file $fake1_properties --stop-at config_file

  [ $status -eq 1 ]

  [[ "${lines[0]}" =~ "$fake1_properties found" ]]
  [[ "${lines[1]}" =~ "ERROR" ]]
  [[ "${lines[2]}" =~ "docker-username requires a non-empty argument" ]]
}

@test "check only jobs in --start_only are starting - start one job only" {
  run bin/app-install.sh --config-file $normal_properties --start-only data-loader --stop-at start_only
  echo "${lines[3]}"

  [ $status -eq 0 ]

  [[ "${lines[0]}" =~ "$normal_properties found" ]]
  [[ "${lines[2]}" =~ "Data Loader" && "${lines[2]}" =~ "yes" ]]
  [[ "${lines[3]}" =~ "Transform Data" && "${lines[3]}" =~ "no" ]]
  [[ "${lines[4]}" =~ "Batch K Means" && "${lines[4]}" =~ "no" ]]
  [[ "${lines[5]}" =~ "Anomaly Detection" && "${lines[5]}" =~ "no" ]]
  [[ "${lines[6]}" =~ "Visualizer" && "${lines[6]}" =~ "no" ]]
}

@test "check only jobs in --start_only are starting - start multiple jobs" {
  run bin/app-install.sh --config-file $normal_properties --start-only data-loader --start-only transform-data --stop-at start_only
  echo "${lines[3]}"

  [ $status -eq 0 ]

  [[ "${lines[0]}" =~ "$normal_properties found" ]]
  [[ "${lines[2]}" =~ "Data Loader" && "${lines[2]}" =~ "yes" ]]
  [[ "${lines[3]}" =~ "Transform Data" && "${lines[3]}" =~ "yes" ]]
  [[ "${lines[4]}" =~ "Batch K Means" && "${lines[4]}" =~ "no" ]]
  [[ "${lines[5]}" =~ "Anomaly Detection" && "${lines[5]}" =~ "no" ]]
  [[ "${lines[6]}" =~ "Visualizer" && "${lines[6]}" =~ "no" ]]
}
