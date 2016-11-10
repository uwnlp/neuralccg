package(default_visibility = ["//visibility:public"])

cc_library(
    name = "gtest-lib",
    srcs = [
        "googletest/src/gtest-all.cc",
    ],
    hdrs = glob([
        "googletest/**/*.h",
        "googletest/src/*.cc",
    ]),
    includes = [
        "googletest",
        "googletest/include",
    ],
)
