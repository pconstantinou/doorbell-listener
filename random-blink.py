#!/usr/bin/python
"""
This example will blink an LED at random
"""
import time, random, pibrella

for i in range(0,3):
  with random.choice(pibrella.light) as l: # Pick a random light
    l.on()
    time.sleep(0.25)
    l.off()
    time.sleep(0.2)
