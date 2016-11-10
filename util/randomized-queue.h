#ifndef NEURALCCG_UTIL_RANDOMIZED_QUEUE_H_
#define NEURALCCG_UTIL_RANDOMIZED_QUEUE_H_

#include <mutex>
#include <condition_variable>
#include "easylogging++.h"

namespace neuralccg {

template<typename T>
class RandomizedQueue {
 public:
  RandomizedQueue() : min_size(0), max_size(0) {
  }

  RandomizedQueue(unsigned min_size, unsigned max_size) : min_size(min_size), max_size(max_size) {
    assert(max_size > 0);
    queue.reserve(max_size);
  }

  void Push(const T &item) {
    std::unique_lock<std::mutex> lock(mutex);
    has_room_condition.wait(lock, [this] { return max_size <= 0 || queue.size() < max_size; });
    queue.push_back(item);
    lock.unlock();
    has_item_condition.notify_one();
  }

  void Pop(T *item) {
    std::unique_lock<std::mutex> lock(mutex);
    has_item_condition.wait(lock, [this] { return queue.size() >= min_size; });
    std::swap(queue.back(), queue[rand() % queue.size()]);
    std::swap(queue.back(), *item);
    queue.pop_back();
    lock.unlock();
    has_room_condition.notify_one();
  }

  size_t size() const {
    return queue.size();
  }

 private:
  std::vector<T> queue;
  std::mutex mutex;
  std::condition_variable has_room_condition;
  std::condition_variable has_item_condition;
  unsigned min_size;
  unsigned max_size;
};

}

#endif
