cmake_minimum_required(VERSION 3.22.1)
project("threadaffinity")

add_library(threadaffinity SHARED thread_affinity.c)

find_library(log-lib log)
target_link_libraries(threadaffinity ${log-lib})
