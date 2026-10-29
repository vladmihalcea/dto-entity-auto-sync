#!/bin/sh
# Stops the campaign cleanly before Hostner's BIOS update (6 Nov, 10:00 CET): RunMatrix checks the STOP file of its
# block before every fork, so the fork in progress finishes, the window summary is written, and run-campaign.sh
# exits because the block has forks left. The file is renewed every 20 seconds, since a driver that starts the next
# block deletes it, until neither the driver nor run-campaign.sh runs.
RESULTS=/root/dto2entity/results/hostner-pair
echo "$(date '+%F %T')  campaign-stop: requesting a stop" >> /root/campaign-stop.log
while pgrep -f '[R]unMatrix' >/dev/null || pgrep -f '[r]un-campaign' >/dev/null; do
    for dir in "$RESULTS"/*/; do
        [ -f "$dir/forks.csv" ] && touch "$dir/STOP"
    done
    sleep 20
done
echo "$(date '+%F %T')  campaign-stop: the campaign has stopped" >> /root/campaign-stop.log
