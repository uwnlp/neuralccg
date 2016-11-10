package(default_visibility = ["//visibility:public"])

cc_library(
    name = "delimited-proto-reader",
    hdrs = ["delimited-proto-reader.h"],
)

cc_library(
    name = "randomized-queue",
    hdrs = ["randomized-queue.h"],
)

cc_library(
    name = "serialization-util",
    hdrs = ["serialization-util.h"],
    deps = ["//neuralccg:protos",
            "@cnn//:cnn-lib"],
)

cc_library(
    name = "timer",
    hdrs = ["timer.h"],
)

cc_test(
    name = "serialization-test",
    srcs = ["serialization-test.cc"],
    deps = ["@gtest//:gtest-lib",
            ":serialization-util"],
)
