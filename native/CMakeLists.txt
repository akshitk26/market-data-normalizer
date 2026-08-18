cmake_minimum_required(VERSION 3.20)
project(market_data_native LANGUAGES CXX)

include(CTest)

set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)
set(CMAKE_EXPORT_COMPILE_COMMANDS ON)

add_library(itch_parser STATIC
    itch-parser/src/itch_parser.cpp
)
target_include_directories(itch_parser PUBLIC itch-parser/include)

add_library(replay_buffer STATIC
    replay-buffer/src/replay_buffer.cpp
)
target_include_directories(replay_buffer PUBLIC replay-buffer/include)

if(BUILD_TESTING)
    add_executable(replay_buffer_test
        replay-buffer/tests/replay_buffer_test.cpp
    )
    target_link_libraries(replay_buffer_test PRIVATE replay_buffer)
    add_test(NAME replay_buffer_test COMMAND replay_buffer_test)
endif()
