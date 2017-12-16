import pifacerelayplus
import time

pfr = pifacerelayplus.PiFaceRelayPlus(pifacerelayplus.RELAY)
pfr.relays[0].toggle()
time.sleep(5)
pfr.relays[0].toggle()
