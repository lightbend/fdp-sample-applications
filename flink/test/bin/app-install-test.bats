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

@test "fail if proper file not generated for uninstall metadata" {
  run bin/app-install.sh --config-file $normal_properties --stop-at metadata_file

  [ $status -eq 0 ]

  [[ "${lines[0]}" =~ "$normal_properties found" ]]
  [[ $(wc -l "${lines[3]}") =~ "5" ]]
  [[ $(sed -n -e "2,2p" "${lines[3]}" | cut -d: -f1) =~ "LOAD_DATA_APP_ID" ]]
  [[ $(sed -n -e "3,3p" "${lines[3]}" | cut -d: -f1) =~ "TOPICS" ]]
  [[ $(sed -n -e "4,4p" "${lines[3]}" | cut -d: -f1) =~ "KAFKA_DCOS_PACKAGE" ]]
}
