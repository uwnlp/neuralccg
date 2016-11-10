#ifndef NEURALCCG_UTIL_TIMER_H_
#define NEURALCCG_UTIL_TIMER_H_

#include "easylogging++.h"

namespace neuralccg {

class Timer {
 public:
  Timer()  {
    previous = clock();
  }

  int Tick() {
    clock_t next = clock();
    int delta = (next - previous) / CLOCKS_PER_SEC;
    previous = next;
    return delta;
  }

 private:
  clock_t previous;
};

}

#endif
