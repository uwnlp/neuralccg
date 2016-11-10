package(default_visibility = ["//visibility:public"])

load("@protobuf//:protobuf.bzl", "cc_proto_library")

cc_proto_library(
    name = "protos",
    srcs = glob(["proto/*.proto"]),
    include = "proto",
    protoc = "@protobuf//:protoc",
    default_runtime = "@protobuf//:protobuf",
)

cc_binary(
    name = "libdecoder.so",
    srcs = ["decoder.h",
            "decoder.cc"],
    linkshared = 1,
    deps = [
        "//neuralccg/model:rnn-parse-scorer",
        "//util:delimited-proto-reader",
        "//jni:jni",
    ],
)
