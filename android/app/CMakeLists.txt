cmake_minimum_required(VERSION 3.22.1)

project(swimvpn_tun2socks_jni C)

add_library(
    swimvpn_tun2socks_jni
    SHARED
    src/main/cpp/tun2socks_jni.c
)

target_compile_features(swimvpn_tun2socks_jni PRIVATE c_std_11)

target_compile_options(
    swimvpn_tun2socks_jni
    PRIVATE
    -Wall
    -Wextra
    -Werror
)

find_library(log-lib log)
find_library(android-lib android)
find_library(dl-lib dl)

target_link_libraries(
    swimvpn_tun2socks_jni
    PRIVATE
    ${log-lib}
    ${android-lib}
    ${dl-lib}
)
