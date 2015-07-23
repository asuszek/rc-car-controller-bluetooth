package rccarcontroller.hackathon.umass.rccarcontroller;

/**
 * A utility class for averaging values using a sliding window.
 */
public class Filter {

    static final int AVERAGE_BUFFER = 2;
    float []filter = new float[AVERAGE_BUFFER];
    int m_idx = 0;

    public float append(float val) {
        filter[m_idx] = val;
        m_idx++;
        if (m_idx == AVERAGE_BUFFER)
            m_idx = 0;
        return average();
    }
    public float average() {
        float sum = 0;
        for (float x : filter)
            sum += x;
        return sum / AVERAGE_BUFFER;
    }

}
