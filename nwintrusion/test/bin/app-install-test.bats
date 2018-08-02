#!/usr/bin/env bats

test_support=test/support
normal_properties="${test_support}/normal.app-install.properties"

@test "fail if configuration properties file not found" {
  run bin/app-install.sh --config-file i-dont-exist.properties --stop-at config_file

  [ $status -eq 1 ]

  [[ "${lines[0]}" =~ "ERROR" ]]
  [[ "${lines[1]}" =~ "i-dont-exist.properties not found" ]]
}

@test "check only jobs in --start_only are starting - start one job only" {
  run bin/app-install.sh --config-file $normal_properties --start-only transform-data --stop-at start_only

  [ $status -eq 0 ]

  [[ "${lines[0]}" =~ "$normal_properties found" ]]
  [[ "${lines[2]}" =~ "Transform Data?" && "${lines[2]}" =~ "yes" ]]
  [[ "${lines[3]}" =~ "Batch K Means" && "${lines[3]}" =~ "no" ]]
  [[ "${lines[4]}" =~ "Anomaly Detection" && "${lines[4]}" =~ "no" ]]
}

@test "check only jobs in --start_only are starting - start multiple jobs" {
  run bin/app-install.sh --config-file $normal_properties --start-only anomaly-detection --start-only transform-data --stop-at start_only

  [ $status -eq 0 ]

  [[ "${lines[0]}" =~ "$normal_properties found" ]]
  [[ "${lines[2]}" =~ "Transform Data?" && "${lines[2]}" =~ "yes" ]]
  [[ "${lines[3]}" =~ "Batch K Means" && "${lines[3]}" =~ "no" ]]
  [[ "${lines[4]}" =~ "Anomaly Detection" && "${lines[4]}" =~ "yes" ]]
}
