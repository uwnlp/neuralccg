package(default_visibility = ["//visibility:public"])

cc_library(
    name = "custom-lstm",
    hdrs = ["custom-lstm.h"],
    srcs = ["custom-lstm.cc"],
    deps = ["//neuralccg:protos",
            "//util:serialization-util",
            "@cnn//:cnn-lib"],
)

cc_library(
    name = "parse-scorer",
    hdrs = ["parse-scorer.h"],
    srcs = ["parse-scorer.cc"],
    deps = ["//neuralccg:protos",
            "//util:serialization-util",
            "@cnn//:cnn-lib"],
)

cc_library(
    name = "rnn-parse-scorer",
    hdrs = ["rnn-parse-scorer.h"],
    srcs = ["rnn-parse-scorer.cc"],
    deps = [":parse-scorer",
            "custom-lstm"],
)
