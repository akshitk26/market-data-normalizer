#include "replay_buffer.hpp"

#include <cassert>

int main() {
    marketdata::native::ReplayBuffer buffer(2);
    buffer.append({1, {1}});
    buffer.append({2, {2}});
    buffer.append({3, {3}});

    const auto replayed = buffer.replay(1, 3);
    assert(replayed.size() == 2);
    assert(replayed[0].sequence_number == 2);
    assert(replayed[1].sequence_number == 3);
    return 0;
}
