import javax.swing.*;
import javax.swing.border.TitledBorder;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultCaret;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.Line2D;
import java.io.*;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.List;

import static java.io.InputStream.nullInputStream;
import static java.lang.Thread.*;
import static javax.swing.Box.createVerticalStrut;

class Graph_Window_Thread extends Thread{

    private final List<List<Object>> list_of_lists_to_draw = new ArrayList<>();
    private boolean anything_to_draw = false;

    @SuppressWarnings("unchecked")
    public void run() {

        for (Field field : Measurements_Thread.class.getDeclaredFields()) {
            try {
                if (field.getType().equals(java.util.List.class)) {
                    list_of_lists_to_draw.add(Arrays.asList(field.getName(), field.get(field.getName())));
                    System.out.println(field.getName() + " " + field.get(field.getName()).getClass());
                }
            } catch (IllegalAccessException iaexc) {
                iaexc.addSuppressed(iaexc);
            }
        }

        for (List<Object> list : list_of_lists_to_draw) {
            if (((List<Object>) list.get(1)).size() > 2) {
                anything_to_draw = true;
                break;
            }
        }

        if (anything_to_draw)
            new Graph_Window(list_of_lists_to_draw);
        else
            ADC_Window.log.append(new Timestamp().out() + "Данных для отрисовки графика нет\n");
    }
}

class Graph_Window extends JFrame {

    private final JSlider slider_X = new JSlider(0,0,0);
    private final JSlider slider_Y = new JSlider(-1600,1600,0);
    private int slider_x_value = 0;
    private float slider_y_value = 0;
    private boolean first_run = true;

    private void setSlider_X_parameters(float x_scale_coeff) {
        System.out.println("---CHANGING SLIDER_X PARAMETERS---");

        if (x_scale_coeff < 0.5f & first_run) {
            slider_X.setEnabled(false);
            slider_X.setValue(slider_x_value);
        } else {
            slider_X.setEnabled(true);
            slider_X.setMinimum(0);
            slider_X.setMaximum((int) (x_scale_coeff * 10f));

            first_run = false; // только коэф превысит значение в 0,5 - первая ветка if больше никогда не сработает

            System.out.println("SLIDER PARAMETERS CHANGED: x_scale_coeff = " + x_scale_coeff + " slider_X.getMaximum() = " + slider_X.getMaximum() /*+ " slider_x_max = " + slider_x_max*/ + " slider_X.getValue() = " + slider_X.getValue());
        }
    }

    private void setSlider_Y_parameters(float y_scale_coeff) {
        System.out.println("---CHANGING SLIDER_Y PARAMETERS--- y_scale_coeff = " + y_scale_coeff);
        slider_Y.setMinimum(- Math.round(y_scale_coeff - 0.5f) * 20); // -0.5f - чтобы избежать отрицательно y_scale_coeff при применении минимального масштаба
        Hashtable<Integer, JComponent> dict = new Hashtable<>(); // подрисовываем отметку на нулевом масштабе
        JLabel tick = new JLabel("I");
        tick.setFont(new Font("Arial", Font.BOLD, 5));
        dict.put(0, tick);
        slider_Y.setLabelTable(dict);
        slider_Y.setPaintLabels(true);
        System.out.println("SLIDER PARAMETERS CHANGED: y_scale_coeff = " + y_scale_coeff + " slider_Y.getMinimum() = " + slider_Y.getMinimum() + " slider_Y.getValue() = " + slider_Y.getValue());
    }

    @SuppressWarnings("unchecked")
    Graph_Window(List<List<Object>> list_of_lists_to_draw) {
        super("Graph@VinzavodBatteryMonitor");
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        getContentPane().setLayout(new BoxLayout(getContentPane(), BoxLayout.Y_AXIS));
        setIconImage(Toolkit.getDefaultToolkit().getImage(this.getClass().getClassLoader().getResource("2.png")));

        Graph_Panel gr = new Graph_Panel(list_of_lists_to_draw);
        gr.setOpaque(false);
        JScrollPane scrollPane = new JScrollPane(gr);
        add(scrollPane);

        scrollPane.getHorizontalScrollBar().addAdjustmentListener((scrolling) -> {     // при прокрутке меняем фокус, не меняем старую ширину; меняем координаты Viewport для отрисовки линии фокуса
            if (gr.x_center_view < gr.getPreferredSize().width) {                      // иначе при крупном масштабе сбивался Viewport влево
                gr.set_x_center_view_gr_width_old(scrollPane.getHorizontalScrollBar().getValue() + (scrollPane.getViewport().getWidth() / 2.0f), gr.gr_width_old);
            }
            gr.setViewport_coord((int) scrollPane.getViewport().getViewRect().getX(), (int) scrollPane.getViewport().getViewRect().getY(), (int) scrollPane.getViewport().getViewRect().getWidth(), (int) scrollPane.getViewport().getViewRect().getHeight());
        });

        scrollPane.getVerticalScrollBar().addAdjustmentListener((scrolling) -> {
            if ((gr.y_center_view + scrollPane.getViewport().getViewRect().getHeight() / 2) < gr.getPreferredSize().height) { // иначе при уменьшении масштаба происходило смещение Viewport вверх
                gr.set_y_center_view_gr_height_old(scrollPane.getVerticalScrollBar().getValue() + (scrollPane.getViewport().getHeight() / 2.0f), gr.gr_height_old);
            }
            gr.setViewport_coord((int) scrollPane.getViewport().getViewRect().getX(), (int) scrollPane.getViewport().getViewRect().getY(), (int) scrollPane.getViewport().getViewRect().getWidth(), (int) scrollPane.getViewport().getViewRect().getHeight());
        });

        ToolTipManager.sharedInstance().setInitialDelay(0);
        ToolTipManager.sharedInstance().setReshowDelay(0);
        ToolTipManager.sharedInstance().setDismissDelay(99999);

        addWindowFocusListener(new WindowAdapter() {
            @Override
            public void windowGainedFocus(WindowEvent e) {
                scrollPane.getViewport().getView().addMouseMotionListener(new MouseMotionAdapter() {
                    @Override
                    public void mouseMoved(MouseEvent e) {

                        String tip_string = "<html>";
                        for (List<Object> list : list_of_lists_to_draw) {
                            if (!((List<Object>) list.get(1)).isEmpty()) {
                                if (list.get(1) instanceof List & ((List<Object>) list.get(1)).get(0) instanceof Float) {
                                    String heading_of_list = list.get(0).toString();
                                    switch (heading_of_list) {
                                        case "yData_temp":
                                            if ((e.getX() - 30) * gr.x_scale_coeff < ((List<Float>) list.get(1)).size() & (e.getX() - 30) >= 0)
                                                tip_string = tip_string.concat("<br />Температура платы: " + BigDecimal.valueOf(((List<Float>) list.get(1)).get((int) ((e.getX() - 30) * gr.x_scale_coeff))).setScale(2, RoundingMode.HALF_EVEN) + " \u00b0С");
                                            break;
                                        case "yData_cur":
                                            if ((e.getX() - 30) * gr.x_scale_coeff < ((List<Float>) list.get(1)).size() & (e.getX() - 30) >= 0)
                                                tip_string = tip_string.concat("Мгновенный ток: " + BigDecimal.valueOf(((List<Float>) list.get(1)).get((int) ((e.getX() - 30) * gr.x_scale_coeff))).setScale(2, RoundingMode.HALF_EVEN) + " A");
                                            break;
                                        case "yData_avg_cur":
                                            if ((e.getX() - 30) * gr.x_scale_coeff < ((List<Float>) list.get(1)).size() & (e.getX() - 30) >= 0)
                                                tip_string = tip_string.concat("<br />Средний ток: " + BigDecimal.valueOf(((List<Float>) list.get(1)).get((int) ((e.getX() - 30) * gr.x_scale_coeff))).setScale(2, RoundingMode.HALF_EVEN) + " A");
                                            break;
                                        case "yData_volt":
                                            if ((e.getX() - 30) * gr.x_scale_coeff < ((List<Float>) list.get(1)).size() & (e.getX() - 30) >= 0)
                                                tip_string = tip_string.concat("<br />Напряжение: " + BigDecimal.valueOf(((List<Float>) list.get(1)).get((int) ((e.getX() - 30) * gr.x_scale_coeff))).setScale(2, RoundingMode.HALF_EVEN) + " В");
                                            break;
                                        case "yData_shunt_volt":
                                            if ((e.getX() - 30) * gr.x_scale_coeff < ((List<Float>) list.get(1)).size() & (e.getX() - 30) >= 0)
                                                tip_string = tip_string.concat("<br />Напряжение на шунте: " + BigDecimal.valueOf(((List<Float>) list.get(1)).get((int) ((e.getX() - 30) * gr.x_scale_coeff))).setScale(2, RoundingMode.HALF_EVEN) + " В");
                                            break;
                                        default:
                                            if ((e.getX() - 30) * gr.x_scale_coeff < ((List<Float>) list.get(1)).size() & (e.getX() - 30) >= 0)
                                                tip_string = tip_string.concat("<br />Другие данные: " + BigDecimal.valueOf(((List<Float>) list.get(1)).get((int) ((e.getX() - 30) * gr.x_scale_coeff))).setScale(2, RoundingMode.HALF_EVEN) + "</html>");
                                    }
                                }
                            }
                        }

                        gr.mouseLine.setLine(e.getX(), scrollPane.getVerticalScrollBar().getValue(), e.getX(), gr.getHeight());
                        gr.repaint(scrollPane.getViewport().getViewRect()); // перерисовываем только видимую часть панели с графиком
                        if (!tip_string.equals("<html>"))
                            gr.setToolTipText(tip_string);
                        else gr.setToolTipText(null);
                    }
                });
            }
            @Override
            public void windowLostFocus(WindowEvent e) {
                for (MouseMotionListener listener : scrollPane.getViewport().getView().getMouseMotionListeners()) {
                    gr.mouseLine.setLine(0,0,0,0);
                    scrollPane.getViewport().getView().removeMouseMotionListener(listener);
                }
                gr.setToolTipText(null);
                gr.repaint();
            }
        });

        scrollPane.getViewport().getView().addMouseListener(new MouseAdapter() {
            @Override
            public void mouseExited(MouseEvent e) {
                gr.setToolTipText(null);
                gr.mouseLine.setLine(0,0,0,0);
            }
        });

        Box box_buttons_scale_x = Box.createHorizontalBox();
        box_buttons_scale_x.setOpaque(false);
        box_buttons_scale_x.setBorder(new TitledBorder(null, "Масштаб по оси X", TitledBorder.DEFAULT_JUSTIFICATION, TitledBorder.DEFAULT_POSITION, new Font("Tahoma", Font.BOLD, 12)));
        box_buttons_scale_x.setPreferredSize(new Dimension(300, 50));
        box_buttons_scale_x.add(slider_X);

        JButton button_plus_y_scale = new JButton("+");
        JButton button_minus_y_scale = new JButton("-");
        button_plus_y_scale.addActionListener((event) ->
                slider_Y.setValue(slider_Y.getValue() + 1)
        );

        Box box_buttons_scale_y = Box.createHorizontalBox();
        box_buttons_scale_y.setOpaque(false);
        box_buttons_scale_y.setBorder(new TitledBorder(null, "Масштаб по оси Y", TitledBorder.DEFAULT_JUSTIFICATION, TitledBorder.DEFAULT_POSITION, new Font("Tahoma", Font.BOLD, 12)));
        box_buttons_scale_y.add(button_minus_y_scale);
        box_buttons_scale_y.add(slider_Y);

        button_minus_y_scale.addActionListener((event) ->
                slider_Y.setValue(slider_Y.getValue() - 1)
        );
        box_buttons_scale_y.add(button_plus_y_scale);

        JPanel panel_scaling = new JPanel();
        panel_scaling.add(box_buttons_scale_x, BorderLayout.WEST);
        panel_scaling.add(box_buttons_scale_y, BorderLayout.EAST);
        panel_scaling.setOpaque(false);
        add(panel_scaling);

        ////// ПЕРВАЯ ОТРИСОВКА ПАНЕЛИ С ГРАФИКОМ ////////
        gr.set_ONLY_FIRST_X_scale_coeff();
        gr.set_MIN_MAX_Values_of_Y();
        gr.set_ONLY_FIRST_Y_scale_coeff();
        gr.set_preferred_width();
        gr.set_preferred_height();
        gr.revalidate();
        setSlider_X_parameters(gr.x_scale_coeff + slider_x_value / 10f);
        setSlider_Y_parameters(gr.y_scale_coeff - slider_y_value / 20f); // тут slider_y_value всегда должен быть = 0

        slider_X.addChangeListener((ChangeEvent) -> {
            slider_x_value = slider_X.getValue();
            gr.change_X_scale_coeff((float) slider_x_value / 10f, scrollPane.getViewport().getSize());
            gr.revalidate();
            gr.set_preferred_width();
            gr.revalidate();
            setSlider_X_parameters(gr.x_scale_coeff + slider_x_value / 10f);
            revalidate();

            scrollPane.getViewport().setViewPosition(new Point(Math.round(((gr.getWidth() * gr.x_center_view) / gr.gr_width_old) - (scrollPane.getViewport().getWidth() / 2.0f)), scrollPane.getVerticalScrollBar().getValue()));
            gr.set_x_center_view_gr_width_old(scrollPane.getHorizontalScrollBar().getValue() + (scrollPane.getViewport().getWidth() / 2.0f), gr.getWidth());

            System.out.println("SLIDER LISTENER TRIGGERED: gr.x_scale_coeff = " + gr.x_scale_coeff + " slider_X.getValue() " + slider_X.getValue() + " slider_X.getMaximum() " + slider_X.getMaximum());

            repaint();
        });

        slider_Y.addChangeListener((ChangeEvent) -> {
            if (slider_y_value < 0) // отрицательная шкала умножена в 20 раз чтобы коррелировать с положительной частью
                slider_y_value = slider_Y.getValue() / 20f;
            else
                slider_y_value = slider_Y.getValue();
            gr.change_Y_scale_coeff(slider_y_value, scrollPane.getViewport().getSize());
            gr.revalidate();
            gr.set_preferred_height(scrollPane.getViewport().getHeight());
            gr.revalidate();
            setSlider_Y_parameters(gr.y_scale_coeff - slider_y_value); // получаем y_scale_coeff без уже примененного y_scale_additional_coef, который = slider_y_value
            revalidate();

            scrollPane.getViewport().setViewPosition(new Point(scrollPane.getHorizontalScrollBar().getValue(), Math.round(((gr.getHeight() * gr.y_center_view) / gr.gr_height_old) - (scrollPane.getViewport().getHeight() / 2.0f))));
            gr.set_y_center_view_gr_height_old(scrollPane.getVerticalScrollBar().getValue() + (scrollPane.getViewport().getHeight() / 2.0f), gr.getHeight());

            System.out.println("SLIDER LISTENER TRIGGERED: gr.y_scale_coeff = " + gr.y_scale_coeff + " slider_Y.getValue() " + slider_Y.getValue() + " slider_Y.getMaximum() " + slider_Y.getMaximum());

            repaint();
        });

        pack();
        setLocationRelativeTo(null);      // перенес после того, как сформируется размер JPanel gr
        setVisible(true);

        gr.set_x_center_view_gr_width_old(scrollPane.getHorizontalScrollBar().getValue() + (scrollPane.getViewport().getWidth() / 2.0f), gr.getWidth()); //Viewport появляется только при отображении окна
        gr.set_y_center_view_gr_height_old(scrollPane.getVerticalScrollBar().getValue() + (scrollPane.getViewport().getHeight() / 2.0f), gr.getHeight());

        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) { // если добавить до pack(), то срабатывает при первой отрисовке окна

                System.out.println("---COMPONENT_RESIZED (Frame)---");
                scrollPane.setPreferredSize(getSize());  // применяю заведомо больший, чем возможно размер, чтобы панель с графиком заняла максимум окна
                scrollPane.revalidate();

                revalidate();
                repaint();

                gr.change_panel_size(scrollPane.getViewport().getSize());
                gr.setViewport_coord((int) scrollPane.getViewport().getViewRect().getX(), (int) scrollPane.getViewport().getViewRect().getY(), scrollPane.getViewport().getWidth(), scrollPane.getViewport().getHeight());

                setSlider_X_parameters(gr.x_scale_coeff + slider_x_value / 10f);
                setSlider_Y_parameters(gr.y_scale_coeff - slider_y_value); // получаем y_scale_coeff без уже примененного y_scale_additional_coef, который = slider_y_value
            }
        });

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                ADC_Window.group_threads_Graph_Window.interrupt();
            }
        });

        synchronized (VBM.sync) {
            while (!currentThread().isInterrupted()) {
                try {
                    VBM.sync.wait();

                    for (List<Object> list : list_of_lists_to_draw) {
                        if (!((List<Object>) list.get(1)).isEmpty()) {
                            if (((List<Object>) list.get(1)).get(((List<Object>) list.get(1)).size() - 1) instanceof Float) {
                                if ((float) ((List<Object>) list.get(1)).get(((List<Object>) list.get(1)).size() - 1) < gr.min_value) {
                                    gr.min_value = (float) ((List<Object>) list.get(1)).get(((List<Object>) list.get(1)).size() - 1);
                                    System.out.println("---MIN_value changed " + list.get(0).toString() + " = " + gr.min_value);
                                }
                            }
                        }
                    }

                    for (List<Object> list : list_of_lists_to_draw) {
                        if (!((List<Object>) list.get(1)).isEmpty()) {
                            if (((List<Object>) list.get(1)).get(((List<Object>) list.get(1)).size() - 1) instanceof Float) {
                                if ((float) ((List<Object>) list.get(1)).get(((List<Object>) list.get(1)).size() - 1) > gr.max_value) {
                                    gr.max_value = (float) ((List<Object>) list.get(1)).get(((List<Object>) list.get(1)).size() - 1);
                                    System.out.println("---MAX_value changed " + list.get(0).toString() + " = " + gr.max_value);
                                }
                            } /*else if (((List<Object>) list.get(1)).get(((List<Object>) list.get(1)).size() - 1) instanceof Long) { 
                                if (((Long) ((List<Object>) list.get(1)).get(((List<Object>) list.get(1)).size() - 1)).floatValue() > gr.max_value) 
                                gr.max_value = ((Long) ((List<Object>) list.get(1)).get(((List<Object>) list.get(1)).size() - 1)).floatValue(); 
                            }*/
                        }
                    }

                    gr.change_panel_size(scrollPane.getViewport().getSize());
                    setSlider_X_parameters(gr.x_scale_coeff + slider_x_value / 10f);
                    setSlider_Y_parameters(gr.y_scale_coeff - slider_y_value); // получаем y_scale_coeff без уже примененного y_scale_additional_coef, который = slider_y_value

                    gr.set_x_center_view_gr_width_old(scrollPane.getHorizontalScrollBar().getValue() + (scrollPane.getViewport().getWidth() / 2.0f), gr.getWidth());
                    gr.set_y_center_view_gr_height_old(scrollPane.getVerticalScrollBar().getValue() + (scrollPane.getViewport().getHeight() / 2.0f), gr.getHeight());

                    gr.repaint();

                } catch (InterruptedException ie) {
                    ADC_Window.group_threads_Graph_Window.interrupt();
                }
            }
        }

    }
}

class Graph_Panel extends JPanel {

    private final List<List<Object>> list_of_lists_to_draw;

    Graph_Panel(List<List<Object>> list_of_lists_to_draw) {
        this.list_of_lists_to_draw = list_of_lists_to_draw;
    }

    float x_scale_coeff = 1.0f;
    private float x_scale_additional_coef = 0f;
    float y_scale_coeff = 1.0f;
    private float y_scale_additional_coef = 0f;
    float max_value = 0f;
    float min_value = 0f;
    float x_center_view;
    float y_center_view;
    int gr_width_old;
    int gr_height_old;
    private int viewport_x = 0;
    private int viewport_y = 0;
    private int viewport_width;
    private int viewport_height;
    private final int gap_Y_below = 124; // 100 - на подписи оси Х, 24 - еще на отступ от нижней части графика (должно быть кратно 12 - зазор между линиями сетки по Y)
    private final int gap_above_max_value_of_graphic = 30; // 30 - зазор от максимальной точки графика по Y до края окна сверху
    private int additional_gap_above_max_value = 0; // применяется когда масштаб меньше чтобы заполнить весь JViewport
    private int additional_gap_below_min_value = 0; // применяется когда масштаб меньше чтобы заполнить весь JViewport

    Line2D mouseLine = new Line2D.Float();

    void change_panel_size(Dimension JViewport_size) {
        System.out.println("---CHANGING_panel_size: JViewport_size = " + JViewport_size);

        x_scale_coeff = getMaximumSizeofAllLists() / ((float) JViewport_size.width - 60) - x_scale_additional_coef;
        change_Y_scale_coeff(y_scale_additional_coef, JViewport_size);

        set_preferred_width();
        set_preferred_height(JViewport_size.height);
        revalidate();
        repaint();
    }

    @SuppressWarnings("unchecked")
    private float getMaximumSizeofAllLists() {
        float max_size = 0f;
        for (List<Object> list : list_of_lists_to_draw) {
            if (!((List<Object>) list.get(1)).isEmpty() & ((List<Object>) list.get(1)).size() > max_size) {
                max_size = ((List<Object>) list.get(1)).size();
            }
        }
        return max_size;
    }

    void setViewport_coord(int viewport_x, int viewport_y, int viewport_width, int viewport_height) {
        this.viewport_x = viewport_x;
        this.viewport_y = viewport_y;
        this.viewport_width = viewport_width;
        this.viewport_height = viewport_height;
    }

    void set_ONLY_FIRST_X_scale_coeff() {
        // 60 - отступы перед и после графика, 3 - добавляет JScrollPane на свои бордюры, 14 - разница по ширине между JFrame и JRootPane - ТАК БЫЛО РАНЬШЕ
        // теперь размер JFrame зависит от размера панели с графиком
        x_scale_coeff = getMaximumSizeofAllLists() / (Toolkit.getDefaultToolkit().getScreenSize().width - 200 - 60) - x_scale_additional_coef;
        System.out.println("FIRST X_SCALE_COEF IS SET: " + x_scale_coeff);
    }

    void set_preferred_width() {
        setPreferredSize(new Dimension(Math.round((getMaximumSizeofAllLists() / x_scale_coeff) + 60), (int) getPreferredSize().getHeight())); // (60-30) - зазор от конца графика до правой части окна (30 съедается отступом перед отрисовкой графика)
        System.out.println("SET_PREFFERED_WIDTH: " + getPreferredSize() + " x_scale_coeff = " + x_scale_coeff);
    }

    void change_X_scale_coeff(float x_scale_additional_coef, Dimension JViewport_size) {
        this.x_scale_additional_coef = x_scale_additional_coef;
        x_scale_coeff = getMaximumSizeofAllLists() / ((float) JViewport_size.width - 60) - x_scale_additional_coef;
    }

    void change_Y_scale_coeff(float y_scale_additional_coef, Dimension JViewport_size) {
        this.y_scale_additional_coef = y_scale_additional_coef;
        if (min_value >= 0f) {
            y_scale_coeff = y_scale_additional_coef + (float) (JViewport_size.height - gap_Y_below - gap_above_max_value_of_graphic) / max_value;
        } else {
            y_scale_coeff = y_scale_additional_coef + (float) (JViewport_size.height - gap_Y_below - gap_above_max_value_of_graphic) / (max_value + Math.abs(min_value));
        }
    }

    @SuppressWarnings("unchecked")
    void set_MIN_MAX_Values_of_Y() {
        ArrayList<Float> max_values = new ArrayList<>();
        ArrayList<Float> min_values = new ArrayList<>();
        for (List<Object> list : list_of_lists_to_draw) {
            if (!((List<Object>) list.get(1)).isEmpty()) {
                if (((List<Object>) list.get(1)).get(0) instanceof Float) {
                    max_values.add(Collections.max((List<Float>) list.get(1)));
                    min_values.add(Collections.min((List<Float>) list.get(1)));
                }/* else if (((List<Object>) list.get(1)).get(0) instanceof Long) { 
                    max_values.add((float) Collections.max((List<Long>) list.get(1))); 
                    min_values.add((float) Collections.min((List<Long>) list.get(1))); 
                }*/
            }
        }
        max_value = Collections.max(max_values);
        min_value = Collections.min(min_values);
    }

    void set_ONLY_FIRST_Y_scale_coeff() {
        if (min_value >= 0) {
            // ТАК БЫЛО РАНЬШЕ: 37 - разница между JFrame и JRootPane; 3 - рамки JScrollPane; 30 - отступ от максимальной точки графика до верхней границы окна
            // ТЕПЕРЬ: размер JFrame зависит от размера панели
            y_scale_coeff = y_scale_additional_coef + (float) (Toolkit.getDefaultToolkit().getScreenSize().height - 300 - gap_Y_below - gap_above_max_value_of_graphic) / max_value;
            System.out.println("MIN_Value >= 0: Y_SCALE_COEF changed = " + y_scale_coeff + " MIN_Value = " + min_value + " gap_above_max_value_of_graphic = " + gap_above_max_value_of_graphic);
        } else {
            y_scale_coeff = y_scale_additional_coef + (float) (Toolkit.getDefaultToolkit().getScreenSize().height - 300 - gap_Y_below - gap_above_max_value_of_graphic) / (max_value + Math.abs(min_value));
            System.out.println("MIN_Value < 0: Y_SCALE_COEF changed = " + y_scale_coeff + " MIN_Value = " + min_value + " gap_above_max_value_of_graphic = " + gap_above_max_value_of_graphic);
        }
    }

    void set_preferred_height() {
        // установка высоты по-старому: для первой отрисовки окна, когда размер JVIEWPORT еще не известен
        if (min_value >= 0) {
            setPreferredSize(new Dimension((int) getPreferredSize().getWidth(), Math.round(max_value * y_scale_coeff + gap_Y_below + gap_above_max_value_of_graphic)));
        } else {
            setPreferredSize(new Dimension((int) getPreferredSize().getWidth(), Math.round((max_value + Math.abs(min_value)) * y_scale_coeff + gap_Y_below + gap_above_max_value_of_graphic)));
        }
        System.out.println("SET_FIRST_PREFFERED_HEIGHT: " + getPreferredSize() + " y_scale_coeff = " + y_scale_coeff + " gap_Y_below = " + gap_Y_below);
    }

    void set_preferred_height(int JViewport_height) {
        // если  Math.round(...) < высоты JVIEWPORT, то орисовываем панель во весь размер JVIEWPORT
        System.out.println("SETTING_PREFFERED_HEIGHT: max_value = " + max_value + " y_scale_coeff = " + y_scale_coeff + " gap_Y_below = " + gap_Y_below + " gap_above_max_value_of_graphic = " + gap_above_max_value_of_graphic);
        if (min_value >= 0) {
            if (Math.round(max_value * y_scale_coeff + gap_Y_below + gap_above_max_value_of_graphic) >= JViewport_height) {
                setPreferredSize(new Dimension((int) getPreferredSize().getWidth(), Math.round(max_value * y_scale_coeff) + gap_Y_below + gap_above_max_value_of_graphic));
                additional_gap_above_max_value = 0;
                additional_gap_below_min_value = 0;
            } else {
                setPreferredSize(new Dimension((int) getPreferredSize().getWidth(), JViewport_height));
                additional_gap_above_max_value = Math.round((float) (JViewport_height - Math.round(max_value * y_scale_coeff + gap_Y_below + gap_above_max_value_of_graphic)) / 2f);
                additional_gap_below_min_value = additional_gap_above_max_value;
            }
        } else {
            if (Math.round((max_value + Math.abs(min_value)) * y_scale_coeff + gap_Y_below + gap_above_max_value_of_graphic) >= JViewport_height) {
                setPreferredSize(new Dimension((int) getPreferredSize().getWidth(), Math.round((max_value + Math.abs(min_value)) * y_scale_coeff) + gap_Y_below + gap_above_max_value_of_graphic));
                additional_gap_above_max_value = 0;
                additional_gap_below_min_value = 0;
            } else {
                setPreferredSize(new Dimension((int) getPreferredSize().getWidth(), JViewport_height));
                additional_gap_above_max_value = Math.round((float) (JViewport_height - Math.round((max_value + Math.abs(min_value)) * y_scale_coeff + gap_Y_below + gap_above_max_value_of_graphic)) / 2f);
                additional_gap_below_min_value = additional_gap_above_max_value;
            }
        }
        System.out.println("    SET_PREFFERED_HEIGHT: " + getPreferredSize() + " y_scale_coeff = " + y_scale_coeff + " additional_gap_above_max_value = " + additional_gap_above_max_value);
    }

    void set_x_center_view_gr_width_old(float x_center_view, int gr_width_old) {
        this.x_center_view = x_center_view;
        this.gr_width_old = gr_width_old;
    }

    void set_y_center_view_gr_height_old(float y_center_view, int gr_height_old) {
        this.y_center_view = y_center_view;
        this.gr_height_old = gr_height_old;
    }

    private void drawListFloat(Graphics g, List<Float> list_to_draw, Color color) {
        Graphics2D g2d = (Graphics2D) g;
        ((Graphics2D) g).setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        ((Graphics2D) g).setStroke(new BasicStroke(0.5f));

        if (x_scale_coeff > 1.0f) {
            float a;            // координата по X с учетом коэффициента масштаба по Х

            for (int i = 0; i < list_to_draw.size()-1; i++) {
                if ((i + Math.round(x_scale_coeff)) <= (list_to_draw.size() - 1) & (i % Math.round(x_scale_coeff)) == 0) {
                    a = i / x_scale_coeff;
                    if (list_to_draw.get(i) != 0f & list_to_draw.get(i + Math.round(x_scale_coeff)) != 0f) {
                        g2d.setColor(color);
                        if (min_value >= 0)
                            g2d.draw(new Line2D.Float(30 + a, (getHeight() - gap_Y_below - additional_gap_below_min_value - list_to_draw.get(i) * y_scale_coeff), // gap_Y_below - зазор от нижней точки графика до нижнего края окна
                                    30 + a + 1, (getHeight() - gap_Y_below - additional_gap_below_min_value - list_to_draw.get(i + Math.round(x_scale_coeff)) * y_scale_coeff))); // 30 - зазор от левой части окна до начала графика
                        else g2d.draw(new Line2D.Float(30 + a, (getHeight() - gap_Y_below - additional_gap_below_min_value - Math.abs(min_value) * y_scale_coeff - list_to_draw.get(i) * y_scale_coeff), // gap_Y_below - зазор от нижней точки графика до нижнего края окна
                                30 + a + 1, (getHeight() - gap_Y_below - additional_gap_below_min_value - Math.abs(min_value) * y_scale_coeff - list_to_draw.get(i + Math.round(x_scale_coeff)) * y_scale_coeff))); // 30 - зазор от левой части окна до начала графика

                    }
                } else {
                    a = i / x_scale_coeff;
                    if (list_to_draw.get(i) != 0f & list_to_draw.get(i + 1) != 0f) {
                        g2d.setColor(color);
                        if (min_value >= 0)
                            g2d.draw(new Line2D.Float(30 + a, (getHeight() - gap_Y_below - additional_gap_below_min_value - list_to_draw.get(i) * y_scale_coeff),
                                    30 + a, (getHeight() - gap_Y_below - additional_gap_below_min_value - list_to_draw.get(i + 1) * y_scale_coeff)));
                        else g2d.draw(new Line2D.Float(30 + a, (getHeight() - gap_Y_below - additional_gap_below_min_value - Math.abs(min_value) * y_scale_coeff - list_to_draw.get(i) * y_scale_coeff),
                                30 + a, (getHeight() - gap_Y_below - additional_gap_below_min_value - Math.abs(min_value) * y_scale_coeff - list_to_draw.get(i + 1) * y_scale_coeff)));
                    }
                }
            }
        } else {          // если коэф по Х меньше либо равен 1.0

            for (int i = 0; i < list_to_draw.size()-1; i++) {
                if (list_to_draw.get(i) != 0f & list_to_draw.get(i + 1) != 0f) {
                    g2d.setColor(color);
                    if (min_value >= 0)
                        g2d.draw(new Line2D.Float(30 + (i * (1 / x_scale_coeff)), (getHeight() - gap_Y_below - additional_gap_below_min_value - list_to_draw.get(i) * y_scale_coeff),
                                30 + i * (1 / x_scale_coeff) + (1 / x_scale_coeff), (getHeight() - gap_Y_below - additional_gap_below_min_value - list_to_draw.get(i + 1) * y_scale_coeff)));
                    else g2d.draw(new Line2D.Float(30 + (i * (1 / x_scale_coeff)), (getHeight() - gap_Y_below - additional_gap_below_min_value - Math.abs(min_value) * y_scale_coeff - list_to_draw.get(i) * y_scale_coeff),
                            30 + i * (1 / x_scale_coeff) + (1 / x_scale_coeff), (getHeight() - gap_Y_below - additional_gap_below_min_value - Math.abs(min_value) * y_scale_coeff - list_to_draw.get(i + 1) * y_scale_coeff)));
                }
            }
        }
    }

    private void drawArrayLong(Graphics g, Long[] array) {
        Graphics2D g2d = (Graphics2D) g;
        ((Graphics2D) g).setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        ((Graphics2D) g).setStroke(new BasicStroke(0.5f));

        if (x_scale_coeff > 1.0f) {
            float a;            // координата по X с учетом коэффициента масштаба по Х

            for (int i = 0; i < array.length-1; i++) {
                if ((i + Math.round(x_scale_coeff)) <= (array.length - 1) & (i % Math.round(x_scale_coeff)) == 0) {
                    a = i / x_scale_coeff;
                    if (array[i] != 0f & array[i + Math.round(x_scale_coeff)] != 0f) {
                        g2d.setColor(Color.GREEN);
                        if (min_value >= 0)
                            g2d.draw(new Line2D.Float(30 + a, (getHeight() - gap_Y_below - array[i] * y_scale_coeff),
                                    30 + a + 1, (getHeight() - gap_Y_below - array[i + Math.round(x_scale_coeff)] * y_scale_coeff)));
                        else g2d.draw(new Line2D.Float(30 + a, (getHeight() - gap_Y_below - Math.abs(min_value) * y_scale_coeff - array[i] * y_scale_coeff),
                                30 + a + 1, (getHeight() - gap_Y_below - Math.abs(min_value) * y_scale_coeff - array[i + Math.round(x_scale_coeff)] * y_scale_coeff)));
                    }
                } else {
                    a = i / x_scale_coeff;
                    if (array[i] != 0f & array[i + 1] != 0f) {
                        g2d.setColor(Color.GREEN);
                        if (min_value >= 0)
                            g2d.draw(new Line2D.Float(30 + a, (getHeight() - gap_Y_below - array[i] * y_scale_coeff),
                                    30 + a, (getHeight() - gap_Y_below - array[i + 1] * y_scale_coeff)));
                        else g2d.draw(new Line2D.Float(30 + a, (getHeight() - gap_Y_below - Math.abs(min_value) * y_scale_coeff - array[i] * y_scale_coeff),
                                30 + a, (getHeight() - gap_Y_below - Math.abs(min_value) * y_scale_coeff - array[i + 1] * y_scale_coeff)));
                    }
                }
            }

        } else {          // если коэф по Х меньше либо равен 1.0

            for (int i = 0; i < array.length-1; i++) {
                if (array[i] != 0f & array[i + 1] != 0f) {
                    g2d.setColor(Color.GREEN);
                    if (min_value >= 0)
                        g2d.draw(new Line2D.Float(30 + (i * (1 / x_scale_coeff)), (getHeight() - gap_Y_below - array[i] * y_scale_coeff),
                                30 + i * (1 / x_scale_coeff) + (1 / x_scale_coeff), (getHeight() - gap_Y_below - array[i + 1] * y_scale_coeff)));
                    else g2d.draw(new Line2D.Float(30 + (i * (1 / x_scale_coeff)), (getHeight() - gap_Y_below - Math.abs(min_value) * y_scale_coeff - array[i] * y_scale_coeff),
                            30 + i * (1 / x_scale_coeff) + (1 / x_scale_coeff), (getHeight() - gap_Y_below - Math.abs(min_value) * y_scale_coeff - array[i + 1] * y_scale_coeff)));
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void drawLines(Graphics g) {
        Graphics2D g2d = (Graphics2D) g;
        ((Graphics2D) g).setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        ((Graphics2D) g).setStroke(new BasicStroke(0.5f));

        AffineTransform at_font_rotation = new AffineTransform();
        at_font_rotation.rotate(Math.toRadians(-90));
        Font legend_X_font = new Font ("tahoma", Font.PLAIN, 9).deriveFont(at_font_rotation);

        // СЕТКА ГОРИЗОНТАЛЬНАЯ + подписи оси Y (88 - отступ снизу, избегает пересечения с подписиями оси Х) ОТРИСОВЫВАЕТСЯ СВЕРХУ ВНИЗ
        int grid_gab_above = 100;
        if (x_scale_coeff < 0.3) grid_gab_above = 112;
        int scale_Y = 2; // кол-во точек после запятой в подписях шкалы Y
        if (12 / y_scale_coeff <= 0.01f) scale_Y = 3;
        if (12 / y_scale_coeff > 2.0f) scale_Y = 1;
        if (12 / y_scale_coeff > 3.0f) scale_Y = 0;
        for (int j = 12; j <= viewport_height - grid_gab_above; j = j + 12) {
            g.setColor(Color.LIGHT_GRAY);                                  // (j + 12): 12 - зазор между линиями сетки
            g.drawLine(viewport_x, viewport_y + j, viewport_x + viewport_width, viewport_y + j);
            g.setColor(Color.BLACK);
            g.setFont(new Font ("tahoma", Font.PLAIN, 9));
            // (getHeight() - getPreferredSize().getHeight()) - на случай, если менеджер компоновки окна не применил желаемый размер
            // additional_gap_above_max_value устанавливается положительной, когда масштаб по Y меньше, чтобы заполнить весь JVIEWPORT
            g.drawString(String.valueOf(new BigDecimal(max_value + (gap_above_max_value_of_graphic + getHeight() - getPreferredSize().getHeight() + additional_gap_above_max_value
                            - (float) (viewport_y + j)) / y_scale_coeff).setScale(scale_Y, RoundingMode.HALF_EVEN)),
                    viewport_x + 3, viewport_y + j + 3);    // (j + 3): тройка - выравнивание текста в центр линиий сетки
        }

        // СЕТКА ВЕРТИКАЛЬНАЯ + подписи по Х
        float a_max = (Measurements_Thread.yData_avg_cur.size()-1) / x_scale_coeff;           // определяем заранее кол-во точек отрисовки самого графика
        // вычисляем зазор между вертикальными линиями так, чтобы первая и последняя линии сетки совпадали с первой и последней точками графика
        int number_of_vert_lines = getWidth() / 15;
        float line_gap_X = a_max / Math.round(number_of_vert_lines/* * (getWidth() / (super.getRootPane().getParent().getBounds().getWidth() - 3 - 14))*/);

        g.setFont(legend_X_font);
        SimpleDateFormat sdf_out;
        if (x_scale_coeff < 0.3)
            sdf_out = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss,SSS");
        else
            sdf_out = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");

        for (long i = 0; i <= Math.ceil(a_max / line_gap_X); i++) {
            g2d.setColor(Color.lightGray);
            g2d.draw(new Line2D.Float(30 + line_gap_X * i, viewport_y, 30 + line_gap_X * i, viewport_y + viewport_height)); // 30 - отступ перед графиком
            g2d.setColor(Color.BLACK);
            g2d.drawString(sdf_out.format(Measurements_Thread.xData.get((int) ((Measurements_Thread.xData.size() - 1) * i / (int) Math.ceil(a_max / line_gap_X)))),
                    30 + line_gap_X * i + 3, viewport_y + viewport_height - 3); // "+3" и "-3" - выравнивание по центру линий сетки и отступ от края окна
        }

        // ОТРИСОВКА ГРАФИКОВ
        for (List<Object> list : list_of_lists_to_draw) {
            if (!((List<Object>) list.get(1)).isEmpty()) {
                if (list.get(1) instanceof List & ((List<Object>) list.get(1)).get(0) instanceof Float) {
                    String heading_of_list = list.get(0).toString();
                    Color color;
                    switch (heading_of_list) {
                        case "yData_temp":
                            color = Color.magenta;
                            break;
                        case "yData_cur":
                            color = Color.ORANGE;
                            break;
                        case "yData_avg_cur":
                            color = Color.RED;
                            break;
                        case "yData_volt":
                            color = Color.BLUE;
                            break;
                        default:
                            color = Color.BLACK;
                    }
                    drawListFloat(g, (List<Float>) list.get(1), color);

                } else if (list.get(1) instanceof List & ((List<Object>) list.get(1)).get(0) instanceof Long) {
                    Long[] arr = new Long[((List<Long>) list.get(1)).size()];
                    arr = ((List<Long>) list.get(1)).toArray(arr);
                    drawArrayLong(g, arr);
                }
            }
        }

        g2d.setColor(Color.BLACK);
        g2d.draw(mouseLine);
    }

    @Override
    public void paintComponent (Graphics g) {
        drawLines(g);
    }
}

// класс для отрисовки окна
class ADC_Window extends JFrame {
    private static final Integer[] list_items = {150, 350, 400, 500, 600, 700};
    static JComboBox<Integer> list_of_timeouts = new JComboBox<>(list_items);
    static JLabel label_cycle_counter = new JLabel("Количество измерений: 0");
    static JLabel label_inst_cur = new JLabel("<html>Мгновенное значение<br />тока: 0 А</html>");
    static JLabel label_inst_volt = new JLabel("<html>Мгновенное значение<br />напряжения: 0 В</html>");
    static JLabel label_inst_temperature = new JLabel("Температура платы: 0 \u00b0С");
    static JLabel label_watt_hour = new JLabel("<html>Полученный заряд накопительным<br />итогом (мгн ток): 0 Вт*ч</html>");
    static JLabel label_timeout_counter = new JLabel("<html>Количество превышений времени<br />ответа от Мини-Она: 0</html>");
    static JLabel label_avg_response_time = new JLabel ("<html>Среднее время ответа<br />(запрос тока и напряжения): 0 мс</html>");
    static JLabel label_time_of_meas = new JLabel("<html>Общее время измерений:<br />00 д 00:00:00</html>");

    private static JTextField text_field_vvod_Ymax = new JTextField("35",3);
    private static JTextField text_field_vvod_Ymin = new JTextField("-1",3);
    static JTextField text_field_vvod_avg_coefficient = new JTextField("20",3);
    static JTextField text_field_vvod_ip_address = new JTextField("192.168.2.7",15);

    static JLabel label_max_cur = new JLabel ("<html>Макс значение тока<br />за весь период измерений: 0 A</html>");
    static JLabel label_min_cur = new JLabel ("<html>Мин значение тока<br />за весь период измерений: 0 A</html>");
    static JLabel label_avg_cur = new JLabel ("<html>Средн значение тока<br />за последние 00.0 с: 0 A</html>");
    static JLabel label_max_volt = new JLabel ("<html>Макс значение напряжения<br />за весь период измерений: 0 В</html>");
    static JLabel label_min_volt = new JLabel ("<html>Мин значение напряжения<br />за весь период измерений: 0 В</html>");
    static JLabel label_watt_hour_avg = new JLabel("<html>Полученный заряд накопительным<br />итогом (средн ток): 0 Вт*ч</html>");

    static JButton button_start_measurements = new JButton("Начать измерения");
    static private final Integer[] list_years = {LocalDateTime.now().getYear(), LocalDateTime.now().getYear()+1, LocalDateTime.now().getYear()+2};
    static JComboBox<Integer> end_meas_time_List_of_years = new JComboBox<>(list_years);
    private static final Integer[] list_monthes = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12};
    static JComboBox<Integer> end_meas_time_List_of_monthes = new JComboBox<>(list_monthes);
    private static final Integer[] list_days = {1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19,20,21,22,23,24,25,26,27,28,29,30,31};
    static JComboBox<Integer> end_meas_time_List_of_days = new JComboBox<>(list_days);
    private static final Integer[] list_hours = {0,1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19,20,21,22,23};
    static JComboBox<Integer> end_meas_time_List_of_hours = new JComboBox<>(list_hours);
    private static final Integer[] list_minutes = {0,1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19,20,21,22,23,24,25,26,27,28,29,30,31,32,33,34,35,36,37,38,39,40,41,42,43,44,45,46,47,48,49,50,51,
            52,53,54,55,56,57,58,59};
    static JComboBox<Integer> end_meas_time_List_of_minutes = new JComboBox<>(list_minutes);

    static JComboBox<Integer> start_meas_time_List_of_years = new JComboBox<>(list_years);
    static JComboBox<Integer> start_meas_time_List_of_monthes = new JComboBox<>(list_monthes);
    static JComboBox<Integer> start_meas_time_List_of_days = new JComboBox<>(list_days);
    static JComboBox<Integer> start_meas_time_List_of_hours = new JComboBox<>(list_hours);
    static JComboBox<Integer> start_meas_time_List_of_minutes = new JComboBox<>(list_minutes);

    static JTextArea log = new JTextArea( 25, 65);
    JProgressBar progress_bar = new JProgressBar();

    private static ButtonGroup group_option_X_axis = new ButtonGroup();
    private static JTextField text_field_duration_starts = new JTextField("",13);
    private static JTextField text_field_duration_ends = new JTextField("0",13);

    static ThreadGroup group_threads_Graph_Window = new ThreadGroup("Graph_Window_Threads");

    ADC_Window() {
        super("VinzavodBatteryMonitor v.1.3 beta"); //заголовок окна
        setBounds(0, 0, 1200, 480); //размер окна
        setIconImage(Toolkit.getDefaultToolkit().getImage(this.getClass().getClassLoader().getResource("i1.png")));
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        list_of_timeouts.setSelectedItem(list_items[1]);                                          // *************** здесь устанавливается таймаут соединения по умолчанию ******************
        list_of_timeouts.setFont(new Font("Tahoma", Font.BOLD, 11));
        list_of_timeouts.addActionListener((e) -> {
            if (list_of_timeouts.getSelectedItem() != null) {
                int timeout_list_response = (Integer) list_of_timeouts.getSelectedItem(); // записываю ответ в переменную в виде выбранного пункта
                ADC_Window.log.append(new Timestamp().out() + "Таймаут соединения изменен на " + timeout_list_response + " мс\n");
            }
        });

        end_meas_time_List_of_years.setSelectedItem(list_years[1]);
        end_meas_time_List_of_years.setFont(new Font("Tahoma", Font.BOLD, 11));
        end_meas_time_List_of_years.setPreferredSize(new Dimension(55, 20));
        end_meas_time_List_of_monthes.setSelectedItem(list_monthes[(LocalDateTime.now().getMonthValue()) - 1]);
        end_meas_time_List_of_monthes.setFont(new Font("Tahoma", Font.BOLD, 11));
        end_meas_time_List_of_monthes.setPreferredSize(new Dimension(41, 20));
        end_meas_time_List_of_days.setSelectedItem(list_days[(LocalDateTime.now().getDayOfMonth()) - 1]);
        end_meas_time_List_of_days.setFont(new Font("Tahoma", Font.BOLD, 11));
        end_meas_time_List_of_days.setPreferredSize(new Dimension(41, 20));
        end_meas_time_List_of_hours.setSelectedItem(list_hours[LocalTime.now().getHour()]);
        end_meas_time_List_of_hours.setFont(new Font("Tahoma", Font.BOLD, 11));
        end_meas_time_List_of_hours.setPreferredSize(new Dimension(41, 20));
        if ((LocalTime.now().getMinute()) > 58) {
            end_meas_time_List_of_minutes.setSelectedItem(list_minutes[59]);
        }                                                                                                   // иначе выкидывает ошибку по обращении к элементу,
        else {
            end_meas_time_List_of_minutes.setSelectedItem(list_minutes[(LocalTime.now().getMinute()) + 1]);
        }                                                                                                    // номер которого больше размера массива
        end_meas_time_List_of_minutes.setFont(new Font("Tahoma", Font.BOLD, 11));
        end_meas_time_List_of_minutes.setPreferredSize(new Dimension(41, 20));

        start_meas_time_List_of_years.setSelectedItem(list_years[0]);
        start_meas_time_List_of_years.setFont(new Font("Tahoma", Font.BOLD, 11));
        start_meas_time_List_of_years.setPreferredSize(new Dimension(55, 20));
        start_meas_time_List_of_monthes.setSelectedItem(list_monthes[(LocalDateTime.now().getMonthValue()) - 1]);
        start_meas_time_List_of_monthes.setFont(new Font("Tahoma", Font.BOLD, 11));
        start_meas_time_List_of_monthes.setPreferredSize(new Dimension(41, 20));
        start_meas_time_List_of_days.setSelectedItem(list_days[(LocalDateTime.now().getDayOfMonth()) - 1]);
        start_meas_time_List_of_days.setFont(new Font("Tahoma", Font.BOLD, 11));
        start_meas_time_List_of_days.setPreferredSize(new Dimension(41, 20));
        start_meas_time_List_of_hours.setSelectedItem(list_hours[LocalTime.now().getHour()]);
        start_meas_time_List_of_hours.setFont(new Font("Tahoma", Font.BOLD, 11));
        start_meas_time_List_of_hours.setPreferredSize(new Dimension(41, 20));
        if ((LocalTime.now().getMinute()) < 1) {
            start_meas_time_List_of_minutes.setSelectedItem(list_minutes[0]);
        }                                                                                                        // иначе выкидывает ошибку по обращении к элементу,
        else {
            start_meas_time_List_of_minutes.setSelectedItem(list_minutes[(LocalTime.now().getMinute()) - 1]);
        }                                                                                                         // номер которого больше размера массива
        start_meas_time_List_of_minutes.setFont(new Font("Tahoma", Font.BOLD, 11));
        start_meas_time_List_of_minutes.setPreferredSize(new Dimension(41, 20));

        Font tahoma = new Font("Tahoma", Font.BOLD, 12);
        button_start_measurements.setFont(tahoma);
        label_inst_cur.setFont(new Font("Tahoma", Font.PLAIN, 12));
        label_inst_volt.setFont(new Font("Tahoma", Font.PLAIN, 12));
        label_inst_temperature.setFont(new Font("Tahoma", Font.PLAIN, 12));
        label_cycle_counter.setFont(new Font("Tahoma", Font.PLAIN, 12));
        label_watt_hour.setFont(new Font("Tahoma", Font.PLAIN, 12));
        label_timeout_counter.setFont(new Font("Tahoma", Font.PLAIN, 12));
        label_max_cur.setFont(new Font("Tahoma", Font.PLAIN, 12));
        label_min_cur.setFont(new Font("Tahoma", Font.PLAIN, 12));
        label_avg_cur.setFont(new Font("Tahoma", Font.PLAIN, 12));
        label_max_volt.setFont(new Font("Tahoma", Font.PLAIN, 12));
        label_min_volt.setFont(new Font("Tahoma", Font.PLAIN, 12));
        label_watt_hour_avg.setFont(new Font("Tahoma", Font.PLAIN, 12));
        label_avg_response_time.setFont(new Font("Tahoma", Font.PLAIN, 12));
        label_time_of_meas.setFont(new Font("Tahoma", Font.PLAIN, 12));

        JLabel label_timeout = new JLabel("Таймаут соединения, мс:  ");
        label_timeout.setFont(tahoma);
        JLabel label_end_meas_time = new JLabel("Время окончания измерений:    ");
        label_end_meas_time.setFont(tahoma);
        JLabel label_start_meas_time = new JLabel("Время начала измерений:   ");
        label_start_meas_time.setFont(tahoma);
        JLabel label_vvod_ip_adress = new JLabel("IP адрес системы измерений:   ");
        label_vvod_ip_adress.setFont(tahoma);

        JPanel adc_panel_results = new JPanel(new GridLayout(2, 2, 0, 0));
        Box adc_panel_results_1_1 = Box.createVerticalBox();
        adc_panel_results_1_1.setBorder(new TitledBorder(null, "Мгновенные значения", TitledBorder.DEFAULT_JUSTIFICATION, TitledBorder.DEFAULT_POSITION, tahoma));
        adc_panel_results_1_1.add(createVerticalStrut(10));
        adc_panel_results_1_1.add(label_inst_cur);
        adc_panel_results_1_1.add(createVerticalStrut(10));
        adc_panel_results_1_1.add(label_inst_volt);
        adc_panel_results_1_1.add(createVerticalStrut(10));
        adc_panel_results_1_1.add(label_inst_temperature);
        Box adc_panel_results_1_2 = Box.createVerticalBox();
        adc_panel_results_1_2.setBorder(new TitledBorder(null, "Полученный заряд", TitledBorder.DEFAULT_JUSTIFICATION, TitledBorder.DEFAULT_POSITION, tahoma));
        adc_panel_results_1_2.add(createVerticalStrut(10));
        adc_panel_results_1_2.add(label_watt_hour);
        adc_panel_results_1_2.add(createVerticalStrut(10));
        adc_panel_results_1_2.add(label_watt_hour_avg);
        Box adc_panel_results_2_1 = Box.createVerticalBox();
        adc_panel_results_2_1.setBorder(new TitledBorder(null, "Средние значения", TitledBorder.DEFAULT_JUSTIFICATION, TitledBorder.DEFAULT_POSITION, tahoma));
        adc_panel_results_2_1.add(createVerticalStrut(5));
        adc_panel_results_2_1.add(label_min_cur);
        adc_panel_results_2_1.add(createVerticalStrut(2));
        adc_panel_results_2_1.add(label_max_cur);
        adc_panel_results_2_1.add(createVerticalStrut(2));
        adc_panel_results_2_1.add(label_avg_cur);
        adc_panel_results_2_1.add(createVerticalStrut(5));
        adc_panel_results_2_1.add(label_min_volt);
        adc_panel_results_2_1.add(createVerticalStrut(2));
        adc_panel_results_2_1.add(label_max_volt);
        adc_panel_results_2_1.add(createVerticalStrut(5));

        Box adc_panel_results_2_2 = Box.createVerticalBox();
        adc_panel_results_2_2.setBorder(new TitledBorder(null, "Статистические данные", TitledBorder.DEFAULT_JUSTIFICATION, TitledBorder.DEFAULT_POSITION, tahoma));
        adc_panel_results_2_2.add(createVerticalStrut(10));
        adc_panel_results_2_2.add(label_cycle_counter);
        adc_panel_results_2_2.add(createVerticalStrut(5));
        adc_panel_results_2_2.add(label_timeout_counter);
        adc_panel_results_2_2.add(createVerticalStrut(5));
        adc_panel_results_2_2.add(label_avg_response_time);
        adc_panel_results_2_2.add(createVerticalStrut(5));
        adc_panel_results_2_2.add(label_time_of_meas);
        adc_panel_results.add(adc_panel_results_1_1);
        adc_panel_results.add(adc_panel_results_1_2);
        adc_panel_results.add(adc_panel_results_2_1);
        adc_panel_results.add(adc_panel_results_2_2);

        Box box_vvod_ip_adress = Box.createHorizontalBox();
        box_vvod_ip_adress.add(label_vvod_ip_adress);
        text_field_vvod_ip_address.setFont(new Font("Tahoma", Font.BOLD, 11));
        box_vvod_ip_adress.add(text_field_vvod_ip_address);

        Box box_vvod_timeout = Box.createHorizontalBox();
        box_vvod_timeout.add(label_timeout);
        box_vvod_timeout.add(list_of_timeouts); // добавляю на панель выпадающий список

        Box box_vvod_end_meas_time = Box.createHorizontalBox();
        box_vvod_end_meas_time.add(label_end_meas_time);
        box_vvod_end_meas_time.add(end_meas_time_List_of_years);
        box_vvod_end_meas_time.add(end_meas_time_List_of_monthes);
        box_vvod_end_meas_time.add(end_meas_time_List_of_days);
        box_vvod_end_meas_time.add(end_meas_time_List_of_hours);
        box_vvod_end_meas_time.add(end_meas_time_List_of_minutes);

        Box box_vvod_start_meas_time = Box.createHorizontalBox();
        box_vvod_start_meas_time.add(label_start_meas_time);
        box_vvod_start_meas_time.add(start_meas_time_List_of_years);
        box_vvod_start_meas_time.add(start_meas_time_List_of_monthes);
        box_vvod_start_meas_time.add(start_meas_time_List_of_days);
        box_vvod_start_meas_time.add(start_meas_time_List_of_hours);
        box_vvod_start_meas_time.add(start_meas_time_List_of_minutes);

        Box box_vvod_avg_coefficient = Box.createHorizontalBox();
        JLabel label_vvod_avg_coefficient = new JLabel("Количество мгновенных измерений для усреднения тока: ");
        label_vvod_avg_coefficient.setFont(tahoma);
        box_vvod_avg_coefficient.add(label_vvod_avg_coefficient);
        text_field_vvod_avg_coefficient.setFont(new Font("Tahoma", Font.BOLD, 11));
        box_vvod_avg_coefficient.add(text_field_vvod_avg_coefficient);

        JPanel adc_panel_meas_buttons = new JPanel(new GridLayout(3, 1, 0, 2));
        JButton button_stop_measurements = new JButton("Остановить измерения");
        button_stop_measurements.setFont(tahoma);
        button_stop_measurements.addActionListener(new JButton_Stop_Meas_Listener());
        JButton button_refp_measurements = new JButton("Измерить напряжение на REFP");
        button_refp_measurements.setFont(tahoma);
        button_refp_measurements.addActionListener(new JButton_REFP_Listener());
        adc_panel_meas_buttons.add(button_refp_measurements, BorderLayout.SOUTH); // добавляю на панель кнопку начала измерений REFP
        adc_panel_meas_buttons.add(button_start_measurements, BorderLayout.SOUTH);
        button_start_measurements.addActionListener(new JButton_Meas_Listener());
        adc_panel_meas_buttons.add(button_stop_measurements, BorderLayout.SOUTH);

        JPanel adc_panel_graph = new JPanel(new GridBagLayout());
        adc_panel_graph.setBorder(new TitledBorder(null, "Отрисовка графика", TitledBorder.DEFAULT_JUSTIFICATION, TitledBorder.DEFAULT_POSITION, tahoma));
        JLabel label_vvod_Ymax = new JLabel("Максимальное значение шкалы Y:  ");
        label_vvod_Ymax.setFont(tahoma);
        JLabel label_vvod_Ymin = new JLabel("Минимальное значение шкалы Y:    ");
        label_vvod_Ymin.setFont(tahoma);
        text_field_vvod_Ymax.setFont(new Font("Tahoma", Font.BOLD, 11));
        text_field_vvod_Ymin.setFont(new Font("Tahoma", Font.BOLD, 11));
        GridBagConstraints constraints_label_vvod_Ymin = new GridBagConstraints();
        constraints_label_vvod_Ymin.gridx = 0;
        constraints_label_vvod_Ymin.gridy = 0;
        constraints_label_vvod_Ymin.insets = new Insets(2,0,2,0);
        constraints_label_vvod_Ymin.anchor = GridBagConstraints.WEST;
        GridBagConstraints constraints_text_field_vvod_Ymin = new GridBagConstraints();
        constraints_text_field_vvod_Ymin.gridx = 1;
        constraints_text_field_vvod_Ymin.gridy = 0;
        constraints_label_vvod_Ymin.insets = new Insets(2,0,2,0);
        constraints_text_field_vvod_Ymin.anchor = GridBagConstraints.EAST;
        GridBagConstraints constraints_label_vvod_Ymax = new GridBagConstraints();
        constraints_label_vvod_Ymax.gridx = 0;
        constraints_label_vvod_Ymax.gridy = 1;
        constraints_label_vvod_Ymax.anchor = GridBagConstraints.WEST;
        GridBagConstraints constraints_text_field_vvod_Ymax = new GridBagConstraints();
        constraints_text_field_vvod_Ymax.gridx = 1;
        constraints_text_field_vvod_Ymax.gridy = 1;
        constraints_text_field_vvod_Ymax.insets = new Insets(2,0,2,0);
        constraints_text_field_vvod_Ymax.anchor = GridBagConstraints.EAST;
        adc_panel_graph.add(label_vvod_Ymin, constraints_label_vvod_Ymin);
        adc_panel_graph.add(text_field_vvod_Ymin, constraints_text_field_vvod_Ymin);
        adc_panel_graph.add(label_vvod_Ymax, constraints_label_vvod_Ymax);
        adc_panel_graph.add(text_field_vvod_Ymax, constraints_text_field_vvod_Ymax);
        JLabel label_period = new JLabel("<html>Период отрисовки графика:<br />(\"0\" - без ограничений)</html>");
        label_period.setFont(tahoma);
        GridBagConstraints constraints_label_period = new GridBagConstraints();
        constraints_label_period.gridx = 0;
        constraints_label_period.gridy = 3;
        constraints_label_period.gridheight = 3;
        constraints_label_period.insets = new Insets(0,0,0,5);
        constraints_label_period.anchor = GridBagConstraints.EAST;
        adc_panel_graph.add(label_period, constraints_label_period);
        JRadioButton radio_button_fullscale = new JRadioButton("весь период", true);
        radio_button_fullscale.setFont(tahoma);
        radio_button_fullscale.setActionCommand("fullscale");
        group_option_X_axis.add(radio_button_fullscale);
        GridBagConstraints constraints_radio_fullscale = new GridBagConstraints();
        constraints_radio_fullscale.gridx = 1;
        constraints_radio_fullscale.gridy = 3;
        constraints_radio_fullscale.gridwidth = 3;
        constraints_radio_fullscale.anchor = GridBagConstraints.WEST;
        adc_panel_graph.add(radio_button_fullscale, constraints_radio_fullscale);
        JRadioButton radio_button_partscale = new JRadioButton("", false);
        radio_button_partscale.setActionCommand("partscale");
        group_option_X_axis.add(radio_button_partscale);
        text_field_duration_starts.setEnabled(false);
        text_field_duration_ends.setEnabled(false);
        radio_button_partscale.addActionListener((actionEvent)-> {
            text_field_duration_starts.setEnabled(true);
            text_field_duration_ends.setEnabled(true);
        });
        radio_button_fullscale.addActionListener((actionEvent)-> {
            text_field_duration_starts.setEnabled(false);
            text_field_duration_ends.setEnabled(false);
        });

        GridBagConstraints constraints_radio_partscale = new GridBagConstraints();
        constraints_radio_partscale.gridx = 1;
        constraints_radio_partscale.gridy = 4;
        constraints_radio_partscale.gridheight = 2;
        constraints_radio_partscale.anchor = GridBagConstraints.WEST;
        adc_panel_graph.add(radio_button_partscale, constraints_radio_partscale);
        JLabel label_period_with = new JLabel("c");
        label_period_with.setFont(tahoma);
        GridBagConstraints constraints_label_period_with = new GridBagConstraints();
        constraints_label_period_with.gridx = 2;
        constraints_label_period_with.gridy = 4;
        constraints_label_period_with.anchor = GridBagConstraints.WEST;
        adc_panel_graph.add(label_period_with, constraints_label_period_with);
        JButton_Meas_Listener jb = new JButton_Meas_Listener();
        text_field_duration_starts.setText(String.format("%d/%02d/%02d %02d:%02d:%02d",            // беру через метод листенера значения начала измерений и применяю в поле начала отрисовки
                jb.get_start_time().getYear(),
                jb.get_start_time().getMonthValue(),
                jb.get_start_time().getDayOfMonth(),
                jb.get_start_time().getHour(),
                jb.get_start_time().getMinute()+2,
                jb.get_start_time().getSecond()+1));
        text_field_duration_starts.setFont(new Font("Tahoma", Font.BOLD, 11));
        GridBagConstraints constraints_text_field_duration_starts = new GridBagConstraints();
        constraints_text_field_duration_starts.gridx = 3;
        constraints_text_field_duration_starts.gridy = 4;
        constraints_text_field_duration_starts.anchor = GridBagConstraints.WEST;
        adc_panel_graph.add(text_field_duration_starts, constraints_text_field_duration_starts);
        JLabel label_period_po = new JLabel("по ");
        label_period_po.setFont(tahoma);
        GridBagConstraints constraints_label_period_po = new GridBagConstraints();
        constraints_label_period_po.gridx = 2;
        constraints_label_period_po.gridy = 5;
        constraints_label_period_po.insets = new Insets(0,0,0,3);
        constraints_label_period_po.anchor = GridBagConstraints.WEST;
        adc_panel_graph.add(label_period_po, constraints_label_period_po);
        text_field_duration_ends.setFont(new Font("Tahoma", Font.BOLD, 11));
        GridBagConstraints constraints_text_field_duration_ends = new GridBagConstraints();
        constraints_text_field_duration_ends.gridx = 3;
        constraints_text_field_duration_ends.gridy = 5;
        constraints_text_field_duration_ends.anchor = GridBagConstraints.WEST;
        adc_panel_graph.add(text_field_duration_ends, constraints_text_field_duration_ends);
        JButton button_draw_graph = new JButton("Отрисовать график");
        button_draw_graph.setFont(tahoma);
        button_draw_graph.addActionListener((ActionEvent) -> new Thread(group_threads_Graph_Window, new Graph_Window_Thread()).start());

        GridBagConstraints constraints_button_draw_graph = new GridBagConstraints();
        constraints_button_draw_graph.gridx = 0;
        constraints_button_draw_graph.gridy = 6;
        constraints_button_draw_graph.gridwidth = 4;
        constraints_button_draw_graph.insets = new Insets(2,0,2,0);
        constraints_button_draw_graph.fill = GridBagConstraints.HORIZONTAL;
        adc_panel_graph.add(button_draw_graph, constraints_button_draw_graph);

        Box box_settings = Box.createVerticalBox();
        box_settings.add(box_vvod_ip_adress);
        box_settings.add(createVerticalStrut(5));
        box_settings.add(box_vvod_timeout);
        box_settings.add(createVerticalStrut(5));
        box_settings.add(box_vvod_start_meas_time);
        box_settings.add(createVerticalStrut(5));
        box_settings.add(box_vvod_end_meas_time);
        box_settings.add(createVerticalStrut(5));
        box_settings.add(box_vvod_avg_coefficient);
        box_settings.add(createVerticalStrut(5));
        box_settings.add(adc_panel_meas_buttons);
        box_settings.add(adc_panel_graph);

        JPanel panel_calibration = new JPanel();
        Box box_calibration = Box.createVerticalBox();
        JButton button_average_cur_night = new JButton("Вычислить средний ток с 12 ночи до 6-30 утра");
        button_average_cur_night.setFont(tahoma);
        box_calibration.add(button_average_cur_night);
        box_calibration.add(createVerticalStrut(5));
        button_average_cur_night.addActionListener((ActionEvent) -> {
            try {
                ArrayList<String> days_in_memory = new ArrayList<>();
                days_in_memory.add(new SimpleDateFormat("yyyy/MM/dd").format(Measurements_Thread.xData.get(0)));               // беру первый день, встречающийся в массиве дат

                for (int i = 0; i < Measurements_Thread.xData.size(); i++) {
                    if (!days_in_memory.get(days_in_memory.size()-1).equals(new SimpleDateFormat("yyyy/MM/dd").format(Measurements_Thread.xData.get(i)))) {
                        days_in_memory.add(new SimpleDateFormat("yyyy/MM/dd").format(Measurements_Thread.xData.get(i)));
                    }
                }

                SwingUtilities.invokeLater(() ->
                        ADC_Window.log.append(new Timestamp().out() + "В памяти найдены измерения за " + days_in_memory.size() + " дней\n"));

                for (int i = 0; i < days_in_memory.size(); i++) {
                    Date duration_night_starts = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss").parse(
                            days_in_memory.get(i)                                                               // беру год, месяц и день
                                    + " 00:00:00");
                    Date duration_night_ends = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss").parse(
                            days_in_memory.get(i)
                                    + " 06:30:00");
                    Date closest_date_to_00_00_00 = Collections.min(Measurements_Thread.xData, (Date o1, Date o2) -> {
                        long diff1 = Math.abs(o1.getTime() - duration_night_starts.getTime());
                        long diff2 = Math.abs(o2.getTime() - duration_night_starts.getTime());
                        return Long.compare(diff1, diff2);
                    });
                    Date closest_date_to_06_30_00 = Collections.min(Measurements_Thread.xData, (Date o1, Date o2) -> {
                        long diff1 = Math.abs(o1.getTime() - duration_night_ends.getTime());
                        long diff2 = Math.abs(o2.getTime() - duration_night_ends.getTime());
                        return Long.compare(diff1, diff2);
                    });

                    if (closest_date_to_00_00_00.equals(closest_date_to_06_30_00)) {                                   // такое произойдет если в этот день нет данных за ночь
                        final int ii = i;                                                                              // избегаю ошибки variables referenced from a lambda expression must be final or effectively final
                        SwingUtilities.invokeLater(() ->
                                ADC_Window.log.append(new Timestamp().out() + "Нет данных для вычисления среднего тока за ночь "
                                        + days_in_memory.get(ii) + "\n"));
                    } else {
                        float avg_cur_night_sum = 0f;
                        for (Float item : Measurements_Thread.yData_avg_cur.subList(Measurements_Thread.xData.indexOf(closest_date_to_00_00_00), Measurements_Thread.xData.indexOf(closest_date_to_06_30_00))) {
                            avg_cur_night_sum += item;
                        }
                        float average_cur_night = avg_cur_night_sum / Measurements_Thread.yData_avg_cur.subList(Measurements_Thread.xData.indexOf(closest_date_to_00_00_00), Measurements_Thread.xData.indexOf(closest_date_to_06_30_00)).size();

                        SwingUtilities.invokeLater(() ->
                                ADC_Window.log.append(new Timestamp().out() + "Средний ток за ночь с " + new SimpleDateFormat("yyyy/MM/dd HH:mm:ss").format(closest_date_to_00_00_00)
                                        + " по " + new SimpleDateFormat("yyyy/MM/dd HH:mm:ss").format(closest_date_to_06_30_00) + " составил: " + average_cur_night + " А\n"));
                    }
                }
            } catch (ParseException pe) {
                SwingUtilities.invokeLater(() ->
                        ADC_Window.log.append(new Timestamp().out() + "Не верный формат даты\n")
                );
            }
        });

        JButton button_average_refp = new JButton("Вычислить среднюю температуру");
        button_average_refp.setFont(tahoma);
        box_calibration.add(button_average_refp);
        panel_calibration.add(box_calibration);

        JTabbedPane tabbed_pane_VBM = new JTabbedPane();
        tabbed_pane_VBM.setFont(tahoma);
        JPanel adc_panel_settings = new JPanel(new FlowLayout());
        adc_panel_settings.add(box_settings);
        tabbed_pane_VBM.addTab("Управление измерениями", adc_panel_settings);
        tabbed_pane_VBM.addTab("Результаты измерений", adc_panel_results);
        tabbed_pane_VBM.addTab("Калибровка", panel_calibration);

        JMenuBar VBM_menu = new JMenuBar();
        JMenu menu_file = new JMenu("Файл");
        menu_file.setFont(tahoma);
        JMenuItem open = new JMenuItem("Открыть файл...");
        open.setFont(tahoma);
        open.addActionListener(new Open_File_Listener());
        JMenuItem save = new JMenuItem("Сохранить как...");
        save.setFont(tahoma);
        save.addActionListener(new Save_File_Listener());
        menu_file.add(open);
        menu_file.add(save);
        VBM_menu.add(menu_file);
        setJMenuBar(VBM_menu);

        JPanel panel_log = new JPanel(new GridBagLayout());
        log.setFont(new Font("Tahoma", Font.PLAIN, 11));
        panel_log.setPreferredSize(new Dimension(678,384));
        TitledBorder titled_border_log = new TitledBorder("События");
        titled_border_log.setTitleFont(tahoma);
        panel_log.setBorder(titled_border_log);

        GridBagConstraints constraints_log = new GridBagConstraints();
        constraints_log.gridx = 0;
        constraints_log.gridy = 0;
        constraints_log.insets = new Insets(2,0,2,0);
        constraints_log.fill = GridBagConstraints.HORIZONTAL;
        constraints_log.fill = GridBagConstraints.VERTICAL;
        panel_log.add(new JScrollPane(log), constraints_log);

        log.setEditable(false);
        log.setLineWrap(true);
        log.setWrapStyleWord(true);                                                      // перенос слов на новую строку целиком
        DefaultCaret caret = (DefaultCaret) log.getCaret();                              // автоматическая прокрутка лога по заполнению
        caret.setUpdatePolicy(DefaultCaret.ALWAYS_UPDATE);

        JButton button_clear_timeouts_from_log = new JButton("Очистить записи о превышении таймаута и сработке фильтра");
        button_clear_timeouts_from_log.setFont(tahoma);
        button_clear_timeouts_from_log.addActionListener((actionEvent) -> {                                            // очистка лога от сообщений о превышении времени ответа
            try {
                while (ADC_Window.log.getText().contains("Превышено")) {
                    for (int i = 0; i < (ADC_Window.log.getLineCount() - 1); i++) {
                        if ((ADC_Window.log.getLineEndOffset(i)-ADC_Window.log.getLineStartOffset(i)) > 36) {
                            if (ADC_Window.log.getText(ADC_Window.log.getLineStartOffset(i) + 26, 10).startsWith("Превышено")) {
                                ADC_Window.log.replaceRange(null, ADC_Window.log.getLineStartOffset(i), ADC_Window.log.getLineEndOffset(i));
                            }
                        }
                    }
                }
                while (ADC_Window.log.getText().contains("Отфильтрован")) {
                    for (int i = 0; i < (ADC_Window.log.getLineCount() - 1); i++) {
                        if ((ADC_Window.log.getLineEndOffset(i)-ADC_Window.log.getLineStartOffset(i)) > 36) {
                            if (ADC_Window.log.getText(ADC_Window.log.getLineStartOffset(i) + 26, 13).startsWith("Отфильтрован")) {
                                ADC_Window.log.replaceRange(null, ADC_Window.log.getLineStartOffset(i), ADC_Window.log.getLineEndOffset(i));
                            }
                        }
                    }
                }
            } catch (BadLocationException ble) {
                StringWriter sw = new StringWriter();
                ble.printStackTrace(new PrintWriter(sw));
                ADC_Window.log.append(new Timestamp().out() + ble.toString() + "\n");}
        });
        GridBagConstraints constraints_button_clear_timeouts = new GridBagConstraints();
        constraints_button_clear_timeouts.gridx = 0;
        constraints_button_clear_timeouts.gridy = 1;
        constraints_button_clear_timeouts.insets = new Insets(2,0,2,0);
        constraints_button_clear_timeouts.fill = GridBagConstraints.HORIZONTAL;

        panel_log.add(button_clear_timeouts_from_log, constraints_button_clear_timeouts);
        GridBagConstraints constraints_progress_bar = new GridBagConstraints();
        constraints_progress_bar.gridx = 0;
        constraints_progress_bar.gridy = 2;
        constraints_progress_bar.insets = new Insets(2,0,2,0);
        constraints_progress_bar.fill = GridBagConstraints.HORIZONTAL;
        panel_log.add(progress_bar, constraints_progress_bar);

        Box box_VBM_window = Box.createHorizontalBox();
        box_VBM_window.add(tabbed_pane_VBM);
        box_VBM_window.add(panel_log);
        add(box_VBM_window);
        pack();
        setResizable(false);
    }
}

// класс для вывода времени
class Timestamp {
    String out() {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss,SSS");
        return "[" + LocalDateTime.now().format(formatter) + "] ";
    }
}

// класс получения мгновенного тока заряда
class ADC_current {
    private float inst_current;

    float current(String ip, int timeout) {
        try {
            URL url_CSLA = new URL("http://" + ip + "/password/?adsn=0&r0=129&r1=0&r2=192");                      // r0=129 : AIN0 - AVSS; r2=64 : external reference REFP0; r2=192 : external reference AVDD
            URLConnection con_CSLA = url_CSLA.openConnection();                                                        // r0=176 : AIN3 - AVSS
            con_CSLA.setConnectTimeout(timeout);
            con_CSLA.setReadTimeout(timeout);

            if (!con_CSLA.getInputStream().equals(nullInputStream())) {                        // проверка на пустой ответ (например в случае не правильного адреса)
                Scanner input_line = new Scanner(con_CSLA.getInputStream());
                input_line.useDelimiter(",");                                                  // беру ответ Мини-она до первой запятой
                String ain0_raw = input_line.next().replaceAll("\\D+", "");    // записываю ответ Мини-она в переменную, удаляю все не-цифры
                input_line.close();
                int ain0 = Integer.parseInt(ain0_raw);                                          // преобразую String в int
                //inst_current = ((((ain0 / 8388607f) * 5000) / 0.55433f) - 4978.1f) / 33.31f;  // так было изначально
                //inst_current = ((((ain0 / 8388607f) * 4995.93f) / 0.55433f) - 4995.93f) / 32.83f; // чувствительность по даташиту
                //inst_current = ((((ain0 / 8388607f) * 4995.93f) / 0.55433f) - 4998.165f) / 33.31f; // было до ночи на 29/10
                //inst_current = ((((ain0 / 8388607f) * 4996.00f) / 0.55433f) - 4977.294f) / 33.31f; // поменял после ночи на 29/10
                //inst_current = ((((ain0 / 8388607f) * 4996.00f) / 0.55433f) - 5036.401f) / 33.31f; // поменял после ночи на 30/10
                //inst_current = ((((ain0 / 8388607f) * 4996.00f) / 0.55433f) - 5041.919f) / 33.31f; // поменял после ночи на 31/10
                //inst_current = ((((ain0 / 8388607f) * 4996.00f) / 0.55433f) - 5035.626f) / 33.31f; // поменял после ночи на 01/11
                //inst_current = ((((ain0 / 8388607f) * 4996.00f) / 0.55433f) - 5023.884f) / 33.31f; // поменял после ночей на 02-05/11
                inst_current = ((((ain0 / 8388607f) * 4996.00f) / 0.55433f) - 4994.308f) / 33.31f - 0.05f; // поменял после ночи на 07/11
                //inst_current = ((ain0 / 8388607f) * 4996.13f); // для замеров нуля на AIN3
            }

        } catch (MalformedURLException exc) {
            SwingUtilities.invokeLater(() ->
                    ADC_Window.log.append(new Timestamp().out() + "Не верный формат URL системы измерений, измерения остановлены\n")
            );
            Measurements_Thread.currentThread().interrupt();
            VBM.adc_window.progress_bar.setIndeterminate(false);
            ADC_Window.button_start_measurements.setEnabled(true);
        } catch (NoRouteToHostException exc) {
            SwingUtilities.invokeLater(() ->
                    ADC_Window.log.append(new Timestamp().out() + "Нет соединения с сетью, измерения остановлены\n")
            );
            Measurements_Thread.currentThread().interrupt();
            VBM.adc_window.progress_bar.setIndeterminate(false);
            ADC_Window.button_start_measurements.setEnabled(true);
        } catch (IOException exc) {
            if (exc instanceof SocketTimeoutException) {
                inst_current = 0f;
                SwingUtilities.invokeLater(() -> {
                    ADC_Window.log.append(new Timestamp().out() + "Превышено время ожидания ответа при измерении тока\n");
                    Measurements_Thread.timeout_counter = ++Measurements_Thread.timeout_counter;
                });
            } else if (exc instanceof UnknownHostException) {
                SwingUtilities.invokeLater(() ->
                        ADC_Window.log.append(new Timestamp().out() + "Не известный адрес системы измерений, измерения остановлены\n")
                );
                Measurements_Thread.currentThread().interrupt();
                VBM.adc_window.progress_bar.setIndeterminate(false);
                ADC_Window.button_start_measurements.setEnabled(true);
            } else if (exc instanceof SocketException) {
                SwingUtilities.invokeLater(() ->
                        ADC_Window.log.append(new Timestamp().out() + "Нет соединения с сетью (SocketException), измерения остановлены\n")
                );
                Measurements_Thread.currentThread().interrupt();
                VBM.adc_window.progress_bar.setIndeterminate(false);
                ADC_Window.button_start_measurements.setEnabled(true);
            } else {
                ADC_Window.log.append(new Timestamp().out() + "Не известная ошибка IOException, сообщение: " + exc.getMessage() + ", принадлежит классу: " + exc.getClass()
                        + " поймана в потоке: " + Thread.currentThread() + "\n");
            }
        }
        return inst_current;
    }
}

// класс получения мгновенного значения напряжения
class ADC_voltage {
    private float inst_voltage;

    float voltage(String ip, int timeout) {
        try {
            URL url_volt = new URL("http://" + ip + "/password/?adsn=0&r0=145&r1=0&r2=192");  // r0=145: AIN1 - AVSS; r0=161: AIN2 - AVSS; r0=177: AIN3 - AVSS
            URLConnection con_volt = url_volt.openConnection();
            con_volt.setConnectTimeout(timeout);
            con_volt.setReadTimeout(timeout);
            Scanner input_line = new Scanner(con_volt.getInputStream());
            input_line.useDelimiter(",");                                                  // беру ответ Мини-она до первой запятой
            String ain1_raw = input_line.next().replaceAll("\\D+", "");    // записываю ответ Мини-она в переменную, удаляю все не-цифры
            input_line.close();
            int ain1 = Integer.parseInt(ain1_raw);                                          // преобразую String в int
            if (ain1 > 8388607f) {
                inst_voltage = - (((ain1 - 16777216f) / 8388608f) * 4996.13f) / 0.12687f / 1000f;
            } else {
                inst_voltage = (((ain1 / 8388607f) * 4996.13f) / 0.12687f) / 1000f;
            }
            //inst_voltage = (((ain1 / 8388607f) * 4996.13f) / 0.097445f) / 1000f;              // 0.12687f - делитель на входе AIN1; 0.097445f - делитель на входе AIN2

        } catch (MalformedURLException exc) {
            SwingUtilities.invokeLater(() ->
                    ADC_Window.log.append(new Timestamp().out() + "Не верный формат URL системы измерений, измерения остановлены\n")
            );
            Measurements_Thread.currentThread().interrupt();
            VBM.adc_window.progress_bar.setIndeterminate(false);
            ADC_Window.button_start_measurements.setEnabled(true);
        } catch (NoRouteToHostException exc) {
            SwingUtilities.invokeLater(() ->
                    ADC_Window.log.append(new Timestamp().out() + "Нет соединения с сетью, измерения остановлены\n")
            );
            Measurements_Thread.currentThread().interrupt();
            VBM.adc_window.progress_bar.setIndeterminate(false);
            ADC_Window.button_start_measurements.setEnabled(true);
        } catch (IOException exc) {
            if (exc instanceof SocketTimeoutException) {
                inst_voltage = 0f;
                SwingUtilities.invokeLater(() -> {
                    ADC_Window.log.append(new Timestamp().out() + "Превышено время ожидания ответа при измерении напряжения\n");
                    Measurements_Thread.timeout_counter = ++Measurements_Thread.timeout_counter;
                });
            } else if (exc instanceof UnknownHostException) {
                SwingUtilities.invokeLater(() ->
                        ADC_Window.log.append(new Timestamp().out() + "Не известный адрес системы измерений, измерения остановлены\n")
                );
                Measurements_Thread.currentThread().interrupt();
                VBM.adc_window.progress_bar.setIndeterminate(false);
                ADC_Window.button_start_measurements.setEnabled(true);
            } else if (exc instanceof SocketException) {
                SwingUtilities.invokeLater(() ->
                        ADC_Window.log.append(new Timestamp().out() + "Нет соединения с сетью (SocketException), измерения остановлены\n")
                );
                Measurements_Thread.currentThread().interrupt();
                VBM.adc_window.progress_bar.setIndeterminate(false);
                ADC_Window.button_start_measurements.setEnabled(true);
            } else {
                ADC_Window.log.append(new Timestamp().out() + "Не известная ошибка IOException, сообщение: " + exc.getMessage() + ", принадлежит классу: " + exc.getClass()
                        + " поймана в потоке: " + Thread.currentThread() + "\n");
            }
        }
        return inst_voltage;
    }
}

class ADC_shunt_volt {
    private float inst_voltage;

    float voltage(String ip, int timeout) {
        try {
            URL url_volt = new URL("http://" + ip + "/password/?adsn=0&r0=177&r1=0&r2=192");  // r0=161: AIN2 - AVSS; r0=145: AIN1 - AVSS; r0=49: AIN1 - AIN2;  r2=192: reference = analog supply (AVDD - AVSS)
            URLConnection con_volt = url_volt.openConnection();                                   // r0=54: (AIN1 - AIN2 + gain=8); r0=177: AIN3 - AVSS
            con_volt.setConnectTimeout(timeout);
            con_volt.setReadTimeout(timeout);
            Scanner input_line = new Scanner(con_volt.getInputStream());
            input_line.useDelimiter(",");                                                  // беру ответ Мини-она до первой запятой
            String ain1_raw = input_line.next().replaceAll("\\D+", "");    // записываю ответ Мини-она в переменную, удаляю все не-цифры
            input_line.close();
            int ain1 = Integer.parseInt(ain1_raw);                                          // преобразую String в int

            // 0.12687f - делитель на входе AIN1; 0.097445f - делитель на входе AIN2; 0.499859f - делитель на входе AIN3

            /*if (ain1 > 8388607f) { 
                inst_voltage = - ((((ain1 - 16777216f) / 8388608f) * 4996.13f) / 0.499859f) / 1000f; 
            } else { 
                inst_voltage = (((ain1 / 8388607f) * 4996.13f) / 0.499859f) / 1000f; 
            }*/
            inst_voltage = ((((ain1 / 8388607f) * 4995.29f) / 0.499859f) - 4995.29f / 2f) / 66f - 0.157f;
        } catch (MalformedURLException exc) {
            SwingUtilities.invokeLater(() ->
                    ADC_Window.log.append(new Timestamp().out() + "Не верный формат URL системы измерений, измерения остановлены\n")
            );
            Measurements_Thread.currentThread().interrupt();
            VBM.adc_window.progress_bar.setIndeterminate(false);
            ADC_Window.button_start_measurements.setEnabled(true);
        } catch (NoRouteToHostException exc) {
            SwingUtilities.invokeLater(() ->
                    ADC_Window.log.append(new Timestamp().out() + "Нет соединения с сетью, измерения остановлены\n")
            );
            Measurements_Thread.currentThread().interrupt();
            VBM.adc_window.progress_bar.setIndeterminate(false);
            ADC_Window.button_start_measurements.setEnabled(true);
        } catch (IOException exc) {
            if (exc instanceof SocketTimeoutException) {
                inst_voltage = 0f;
                SwingUtilities.invokeLater(() -> {
                    ADC_Window.log.append(new Timestamp().out() + "Превышено время ожидания ответа при измерении напряжения\n");
                    Measurements_Thread.timeout_counter = ++Measurements_Thread.timeout_counter;
                });
            } else if (exc instanceof UnknownHostException) {
                SwingUtilities.invokeLater(() ->
                        ADC_Window.log.append(new Timestamp().out() + "Не известный адрес системы измерений, измерения остановлены\n")
                );
                Measurements_Thread.currentThread().interrupt();
                VBM.adc_window.progress_bar.setIndeterminate(false);
                ADC_Window.button_start_measurements.setEnabled(true);
            } else if (exc instanceof SocketException) {
                SwingUtilities.invokeLater(() ->
                        ADC_Window.log.append(new Timestamp().out() + "Нет соединения с сетью (SocketException), измерения остановлены\n")
                );
                Measurements_Thread.currentThread().interrupt();
                VBM.adc_window.progress_bar.setIndeterminate(false);
                ADC_Window.button_start_measurements.setEnabled(true);
            } else {
                ADC_Window.log.append(new Timestamp().out() + "Не известная ошибка IOException, сообщение: " + exc.getMessage() + ", принадлежит классу: " + exc.getClass()
                        + " поймана в потоке: " + Thread.currentThread() + "\n");
            }
        }
        return inst_voltage;
    }
}

// отдельный поток получения напряжения REFP
class REFP_Measurement_Thread extends Thread {
    private static String ip;
    REFP_Measurement_Thread(String ip_address) {
        ip = ip_address;
    }

    public void run() {
        try {

            /////////////////////////////
            float avg_sum_2 = 0;
            for (float item : Measurements_Thread.temp_comp) {
                float cur_sum_2 = 0;
                cur_sum_2 += item;
                avg_sum_2 = cur_sum_2 / (float) Measurements_Thread.temp_comp.size();
            }
            final float avg_sum = avg_sum_2;
            SwingUtilities.invokeLater(() ->
                    ADC_Window.log.append(new Timestamp().out() + "Средний коэффициент ток / температура = " + avg_sum + "\n")
            );
            /////////////////////////////

            // получаем тестовое напряжение на REFP0
            URL url_refp = new URL("http://" + ip + "/password/?adsn=0&r0=208&r1=0&r2=0");
            URLConnection con_refp = url_refp.openConnection();
            con_refp.setConnectTimeout(350);
            con_refp.setReadTimeout(350);
            Scanner input_line_mon = new Scanner(con_refp.getInputStream());
            input_line_mon.useDelimiter(",");
            String mon_raw = input_line_mon.next();
            mon_raw = mon_raw.replaceAll("\\D+", "");
            int mon = Integer.parseInt(mon_raw);
            float adc_mon = ((mon / 8388607f) * 2048) * 4;
            input_line_mon.close();

            SwingUtilities.invokeLater(() ->
                    ADC_Window.log.append(new Timestamp().out() + "Напряжение REFP = " + adc_mon + " мВ\n")
            );

        } catch (MalformedURLException exc) {
            SwingUtilities.invokeLater(() ->
                    ADC_Window.log.append(new Timestamp().out() + "Не верный формат URL системы измерений, измерения остановлены\n")
            );
            currentThread().interrupt();
        } catch (NoRouteToHostException exc) {
            SwingUtilities.invokeLater(() ->
                    ADC_Window.log.append(new Timestamp().out() + "Нет соединения с сетью\n")
            );
        } catch (IOException exc) {
            if (exc instanceof UnknownHostException) {
                SwingUtilities.invokeLater(() ->
                        ADC_Window.log.append(new Timestamp().out() + "Не известный адрес системы измерений, измерения остановлены\n")
                );
                currentThread().interrupt();
            } else if (exc instanceof SocketException) {
                SwingUtilities.invokeLater(() ->
                        ADC_Window.log.append(new Timestamp().out() + "Нет соединения с сетью (SocketException), измерения остановлены\n")
                );
                Measurements_Thread.currentThread().interrupt();
                VBM.adc_window.progress_bar.setIndeterminate(false);
                ADC_Window.button_start_measurements.setEnabled(true);
            } else {
                SwingUtilities.invokeLater(() ->
                        ADC_Window.log.append(new Timestamp().out() + "Не известная ошибка IOException, сообщение: " + exc.getMessage() + ", принадлежит классу: " + exc.getClass()
                                + " поймана в потоке: REFP_Measurement_Thread\n")       //************ выкидывает Connection refused: connect при неправильном адресе****************
                );
            }
        }
    }
}

// класс измерения температуры
class TEMP_Measurement {

    private float temp;

    float temperature(String ip, int timeout) {
        try {
            URL url_temp = new URL("http://" + ip + "/password/?adsn=0&r0=0&r1=2&r2=0");          // ИЗМЕРЕНИЕ ТЕМПЕРАТУРЫ
            URLConnection con_temp = url_temp.openConnection();
            con_temp.setConnectTimeout(timeout);
            con_temp.setReadTimeout(timeout);

            if (!con_temp.getInputStream().equals(nullInputStream())) {                               // проверка на пустой ответ (например в случае не правильного адреса)
                Scanner input_line_mon = new Scanner(con_temp.getInputStream());
                input_line_mon.useDelimiter(",");
                String mon_raw = input_line_mon.next();
                mon_raw = mon_raw.replaceAll("\\D+", "");
                input_line_mon.close();
                int mon = Integer.parseInt(mon_raw);
                /*if (mon > 8388607f) {                                                                 // ОТРИЦАТЕЛЬНЫЕ ПОКАЗАНИЯ ТЕМПЕРАТУРЫ 
                    temp = -(((16777215f - mon)/ 8388608f) * 2048) * 0.03125f;} 
                else { 
                    temp = ((mon / 8388607f) * 2048) * 0.03125f; 
                }*/
                temp = mon / 8388607f * 0.03125f * 4096;
            }

        } catch (MalformedURLException exc) {
            SwingUtilities.invokeLater(() ->
                    ADC_Window.log.append(new Timestamp().out() + "Не верный формат URL системы измерений, измерения остановлены\n")
            );
            currentThread().interrupt();
        } catch (NoRouteToHostException exc) {
            SwingUtilities.invokeLater(() ->
                    ADC_Window.log.append(new Timestamp().out() + "Нет соединения с сетью\n")
            );
        } catch (IOException exc) {
            if (exc instanceof SocketTimeoutException) {
                temp = 1000f;
                SwingUtilities.invokeLater(() -> {
                    ADC_Window.log.append(new Timestamp().out() + "Превышено время ожидания ответа при измерении температуры\n");
                    Measurements_Thread.timeout_counter = ++Measurements_Thread.timeout_counter;
                });
            }
            else if (exc instanceof UnknownHostException) {
                SwingUtilities.invokeLater(() ->
                        ADC_Window.log.append(new Timestamp().out() + "Не известный адрес системы измерений, измерения остановлены\n")
                );
                currentThread().interrupt();
            } else if (exc instanceof SocketException) {
                SwingUtilities.invokeLater(() ->
                        ADC_Window.log.append(new Timestamp().out() + "Нет соединения с сетью (SocketException), измерения остановлены\n")
                );
                Measurements_Thread.currentThread().interrupt();
                VBM.adc_window.progress_bar.setIndeterminate(false);
                ADC_Window.button_start_measurements.setEnabled(true);
            } else {
                SwingUtilities.invokeLater(() ->
                        ADC_Window.log.append(new Timestamp().out() + "Не известная ошибка IOException, сообщение: " + exc.getMessage() + ", принадлежит классу: " + exc.getClass()
                                + " поймана в потоке: TEMP_Measurement_Thread\n")       //************ выкидывает Connection refused: connect при неправильном адресе****************
                );
            }
        }
        return temp;
    }
}

// класс измерений
class Measurements_Thread extends Thread {

    // объявляем листы массивов переменных измерений
    static List<Date> xData = new ArrayList<>();
    static List<Float> yData_cur = new ArrayList<>();
    static List<Float> yData_volt = new ArrayList<>();
    static List<Float> yData_shunt_volt = new ArrayList<>();
    static List<Float> yData_avg_cur = new ArrayList<>();
    static List<Float> yData_temp = new ArrayList<>();
    static List<Long> connection_time_array = new ArrayList<>();
    static List<Float> temp_comp = new ArrayList<>();

    private final LocalDateTime end_meas_time;

    static long connection_time = 0;
    static int timeout_counter = 0;
    private final int avg_cur_coefficient;                              // количество измерений тока, по которым ведется усреднение
    private final String ip_address;

    private float watt_hour_sum;
    private float watt_hour_sum_avg;

    Measurements_Thread(LocalDateTime end_meas_time, String ip_address, int avg_cur_coefficient) {
        this.end_meas_time = end_meas_time;
        this.avg_cur_coefficient = avg_cur_coefficient;
        this.ip_address = ip_address;
    }

    private void calculate_avg_cur() {
        float avg_cur = 0f;
        int counter = 0;
        try {
            if (yData_cur.size() < avg_cur_coefficient) {
                for (Float item : yData_cur) {
                    if (item != 0) {
                        avg_cur += item;
                    }
                }
                /*if (yData_temp.get(yData_temp.size()-1) <= -2.76f) { 
                    yData_avg_cur.add((avg_cur / yData_cur.size()) + (Math.abs(yData_temp.get(yData_temp.size() - 1)) * -0.325f) - 1.22f); 
                } else if (yData_temp.get(yData_temp.size()-1) <= -1.3f) { 
                    yData_avg_cur.add((avg_cur / yData_cur.size()) + (Math.abs(yData_temp.get(yData_temp.size()-1)) * -0.393f) - 1.076f); // ровный диапазон 
                } else if (yData_temp.get(yData_temp.size()-1) <= -0.72f) { 
                    yData_avg_cur.add((avg_cur / yData_cur.size()) + (Math.abs(yData_temp.get(yData_temp.size()-1)) * -0.464f) - 0.97f); // более менее ровный диапазон 
                } else if (yData_temp.get(yData_temp.size()-1) <= 0f) { 
                    yData_avg_cur.add((avg_cur / yData_cur.size()) + (Math.abs(yData_temp.get(yData_temp.size()-1)) * -0.482f) - 1.42f); 
                } else if (yData_temp.get(yData_temp.size()-1) <= 0.9f) { 
                    yData_avg_cur.add((avg_cur / yData_cur.size()) + (Math.abs(yData_temp.get(yData_temp.size()-1)) * 0.597f) - 1.35f); // поменял знак 
                } else { 
                    yData_avg_cur.add((avg_cur / yData_cur.size()) + (Math.abs(yData_temp.get(yData_temp.size()-1)) * 0.597f) - 1.35f); // поменял знак 
                }*/
                yData_avg_cur.add(avg_cur / yData_cur.size());
            } else {
                for (int i = 1; i <= avg_cur_coefficient; i++) {
                    if (yData_cur.get(yData_cur.size() - i) != 0) {
                        avg_cur = avg_cur + yData_cur.get(yData_cur.size() - i);
                        counter++;
                    }
                }
                /*if (yData_temp.get(yData_temp.size()-1) <= -2.76f) { 
                    yData_avg_cur.add((avg_cur / counter) + (Math.abs(yData_temp.get(yData_temp.size() - 1)) * -0.325f) - 1.22f); 
                } else if (yData_temp.get(yData_temp.size()-1) <= -1.3f) { 
                    yData_avg_cur.add((avg_cur / counter) + (Math.abs(yData_temp.get(yData_temp.size()-1)) * -0.393f) - 1.076f); // ровный диапазон 
                } else if (yData_temp.get(yData_temp.size()-1) <= -0.72f) { 
                    yData_avg_cur.add((avg_cur / counter) + (Math.abs(yData_temp.get(yData_temp.size()-1)) * -0.464f) - 0.97f); // более менее ровный диапазон 
                } else if (yData_temp.get(yData_temp.size()-1) <= 0f) { 
                    yData_avg_cur.add((avg_cur / counter) + (Math.abs(yData_temp.get(yData_temp.size()-1)) * -0.482f) - 1.42f); 
                } else if (yData_temp.get(yData_temp.size()-1) <= 0.9f) { 
                    yData_avg_cur.add((avg_cur / counter) + (Math.abs(yData_temp.get(yData_temp.size()-1)) * 0.597f) - 1.35f); // поменял знак 
                } else { 
                    yData_avg_cur.add((avg_cur / counter) + (Math.abs(yData_temp.get(yData_temp.size()-1)) * 0.597f) - 1.35f); // поменял знак 
                }*/
                yData_avg_cur.add(avg_cur / counter);
            }
        } catch (NullPointerException npe) {
            SwingUtilities.invokeLater(() ->
                    ADC_Window.log.append(new Timestamp().out() + "Поймано NullPointerException в Measurement_Thread (calculate_avg_cur())\n")
            );
        }
    }

    private long calculate_time_of_avg_cur() {
        long time_of_avg_cur = 0;
        if (connection_time_array.size() < avg_cur_coefficient) {
            for (long item : connection_time_array) {
                time_of_avg_cur += item;
            }
        } else {
            time_of_avg_cur = connection_time_array.get(connection_time_array.size() - 1);
            for (int i = 1; i <= (avg_cur_coefficient - 1); i++) {
                time_of_avg_cur += connection_time_array.get(connection_time_array.size() - 1 - i);
            }
        }
        return time_of_avg_cur;
    }

    private float get_watt_hour_sum() {
        if (!yData_cur.isEmpty()) {
            float watt_hour = (yData_cur.get(yData_cur.size() - 1) * (yData_volt.get(yData_volt.size() - 1)) * connection_time) / 1000 / 3600;            // получаю мгновенные Ватт-часы
            watt_hour_sum = watt_hour_sum + watt_hour;
        } else {
            watt_hour_sum = 0f;
        }
        return watt_hour_sum;
    }

    private float get_watt_hour_sum_avg() {
        if (!yData_avg_cur.isEmpty()) {
            float watt_hour_avg = ((yData_avg_cur.get(yData_avg_cur.size() - 1) * yData_volt.get(yData_volt.size() - 1)) * connection_time) / 1000 / 3600;
            watt_hour_sum_avg = watt_hour_sum_avg + watt_hour_avg;
        } else {
            watt_hour_sum_avg = 0f;
        }
        return watt_hour_sum_avg;
    }

    private static float get_min_not_zero_value_from_ArrayList(List<Float> input_ArrayList) {
        float min_value = Collections.min(input_ArrayList);
        if (min_value == 0f) {
            List<Float> min_value_list = new ArrayList<>();
            for (Float item : input_ArrayList) {
                if (item != 0f) {
                    min_value_list.add(item);
                }
            }
            if (!min_value_list.isEmpty()) {
                min_value = Collections.min(min_value_list);}
        }
        return min_value;
    }

    public void run() {
        try {
            // ждем начала времени измерений
            JButton_Meas_Listener jb = new JButton_Meas_Listener();
            if (jb.get_start_time().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli() - LocalDateTime.now().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli() > 0)
                Thread.sleep(jb.get_start_time().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli() - LocalDateTime.now().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli());
            // получаем данные измерений и заполняем листы массивов
            while (!Thread.currentThread().isInterrupted()) {
                LocalDateTime start_time_fact = LocalDateTime.now();
                SwingUtilities.invokeLater(() ->
                        ADC_Window.log.append(new Timestamp().out() + "Измерения начаты по факту наступления заданного времени\n")
                );
                SwingUtilities.invokeLater(() ->
                        VBM.adc_window.progress_bar.setIndeterminate(true)                    // включаю прогрессбар
                );

                do {
                    long t1 = System.currentTimeMillis();
                    float cur = new ADC_current().current(ip_address, Integer.parseInt(String.valueOf(ADC_Window.list_of_timeouts.getSelectedItem()))); // передаю в метод current класса ADC_current таймаут из интерфейса
                    float volt = new ADC_voltage().voltage(ip_address, Integer.parseInt(String.valueOf(ADC_Window.list_of_timeouts.getSelectedItem())));
                    float shunt_volt = new ADC_shunt_volt().voltage(ip_address, Integer.parseInt(String.valueOf(ADC_Window.list_of_timeouts.getSelectedItem())));
                    long t2 = System.currentTimeMillis();
                    connection_time = t2 - t1;                                                 // получаю время замеров тока и напряжения
                    if (connection_time < 0) {
                        connection_time = 1000 + connection_time;
                    }

                    synchronized (VBM.sync) {                                                 //****************** синхронизирую заполнение массивов с отрисовкой графика *************

                        if (cur != 0f & volt != 0f) {                                          // возвращаемое значение тока и напряжения обнуляется при превышении таймаута
                            if ((yData_cur.size() % 50) == 0) {                                 // замер температуры 1 раз из 50 замеров тока
                                long t3 = System.currentTimeMillis();
                                float temperature = new TEMP_Measurement().temperature(ip_address, Integer.parseInt(String.valueOf(ADC_Window.list_of_timeouts.getSelectedItem())));
                                long t4 = System.currentTimeMillis();
                                long connection_time_temp = t4 - t3;
                                connection_time = connection_time + connection_time_temp;
                                if (temperature != 1000f) {                                             // температура принимает значение 1000 при превышении таймаута
                                    yData_temp.add(temperature);
                                } else if (!yData_temp.isEmpty()) {
                                    yData_temp.add(yData_temp.get(yData_temp.size() - 1));
                                }
                            } else if (!yData_temp.isEmpty()) {
                                yData_temp.add(yData_temp.get(yData_temp.size() - 1));
                            }
                            yData_cur.add(cur);
                            yData_volt.add(volt);
                            yData_shunt_volt.add(shunt_volt);
                            xData.add(new Date());
                            connection_time_array.add(connection_time);

                            /*if (yData_cur.size() >= 3) { 
                                new Electromagnetic_Interference_Filter().filter(cur); 
                            }*/

                            //temp_cur_compensation();

                            //if (!yData_temp.isEmpty()) {
                            calculate_avg_cur();                                              // перенес после сработки фильтра, иначе бросок разово попадал в среднее
                            //}
                        }

                        VBM.sync.notify();
                    }

                    long avg_connection_time;

                    if (!connection_time_array.isEmpty()) {
                        if (connection_time_array.size() < avg_cur_coefficient) {
                            avg_connection_time = 0;
                            for (long item : connection_time_array) {
                                avg_connection_time += item;
                            }
                            final long avg_connection_time_out = avg_connection_time;
                            SwingUtilities.invokeLater(() ->
                                    ADC_Window.label_avg_response_time.setText("<html>Среднее время ответа<br />(запрос тока и напряжения): " + avg_connection_time_out / connection_time_array.size() + " мс</html>"));
                        } else {
                            avg_connection_time = connection_time_array.get(connection_time_array.size() - 1);
                            for (int i = 1; i <= (avg_cur_coefficient - 1); i++) {
                                avg_connection_time += connection_time_array.get(connection_time_array.size() - 1 - i);
                            }
                            final long avg_connection_time_out = avg_connection_time;
                            SwingUtilities.invokeLater(() ->
                                    ADC_Window.label_avg_response_time.setText("<html>Среднее время ответа<br />(запрос тока и напряжения): " + avg_connection_time_out / (avg_cur_coefficient - 1) + " мс</html>"));
                        }
                    }

                    final BigDecimal cur_out = new BigDecimal(cur).setScale(2, RoundingMode.HALF_EVEN);
                    final BigDecimal volt_out = new BigDecimal(volt).setScale(2, RoundingMode.HALF_EVEN);
                    final BigDecimal watt_inst_cur_out = BigDecimal.valueOf(get_watt_hour_sum()).setScale(2, RoundingMode.HALF_EVEN);
                    final BigDecimal watt_avg_cur_out = BigDecimal.valueOf(get_watt_hour_sum_avg()).setScale(2, RoundingMode.HALF_EVEN);

                    SwingUtilities.invokeLater(() -> {
                        ADC_Window.label_cycle_counter.setText("Количество измерений: " + yData_cur.size() + " "); // обновление кол-ва шагов в окне
                        ADC_Window.label_inst_cur.setText("<html>Мгновенное значение<br />тока: " + cur_out + " А</html>"); // обновление в окне мгновенного значения тока, округленного до сотых
                        ADC_Window.label_inst_volt.setText("<html>Мгновенное значение<br />напряжения: " + volt_out + " В</html>"); // обновление мгновенного значения напряжения в окне
                        if (!yData_temp.isEmpty()) {
                            ADC_Window.label_inst_temperature.setText("Температура платы: " + BigDecimal.valueOf(yData_temp.get(yData_temp.size() - 1)).setScale(2, RoundingMode.HALF_EVEN) + " \u00b0С");
                        }
                        ADC_Window.label_timeout_counter.setText("<html>Количество превышений времени<br />ответа от Мини-Она: " + timeout_counter + "</html>"); // timeout_counter вычисляется в классах ADC_current и ADC_voltage
                        ADC_Window.label_watt_hour.setText("<html>Полученный заряд накопительным<br />итогом (мгн ток): " + watt_inst_cur_out + " Вт*ч</html>");
                        ADC_Window.label_watt_hour_avg.setText("<html>Полученный заряд накопительным<br />итогом (средн ток): " + watt_avg_cur_out + " Вт*ч</html>");
                    });

                    Duration duration_of_meas = Duration.between(start_time_fact, LocalDateTime.now());
                    String duration_hms = String.format("%02d д %02d:%02d:%02d",
                            duration_of_meas.toDaysPart(),
                            duration_of_meas.toHoursPart(),
                            duration_of_meas.toMinutesPart(),
                            duration_of_meas.toSecondsPart());
                    SwingUtilities.invokeLater(() ->
                            ADC_Window.label_time_of_meas.setText("<html>Общее время измерений:<br />" + duration_hms + "</html>"));

                    try {
                        if (!yData_cur.isEmpty()) {
                            float time_of_avg_cur_in_seconds = calculate_time_of_avg_cur() / 1000f;
                            BigDecimal time_of_avg_cur_in_seconds_out = BigDecimal.valueOf(time_of_avg_cur_in_seconds).setScale(1, RoundingMode.HALF_EVEN);
                            BigDecimal cur_max = BigDecimal.valueOf(Collections.max(yData_cur)).setScale(2, RoundingMode.HALF_EVEN);
                            BigDecimal cur_min = BigDecimal.valueOf(get_min_not_zero_value_from_ArrayList(yData_cur)).setScale(2, RoundingMode.HALF_EVEN);
                            BigDecimal volt_max = BigDecimal.valueOf(Collections.max(yData_volt)).setScale(2, RoundingMode.HALF_EVEN);
                            BigDecimal volt_min = BigDecimal.valueOf(get_min_not_zero_value_from_ArrayList(yData_volt)).setScale(2, RoundingMode.HALF_EVEN);

                            SwingUtilities.invokeLater(() -> {
                                ADC_Window.label_max_cur.setText("<html>Макс значение тока<br />за весь период измерений: " + cur_max + " A</html>");
                                ADC_Window.label_min_cur.setText("<html>Мин значение тока<br />за весь период измерений: " + cur_min + " A</html>");
                                if (!yData_avg_cur.isEmpty()) {
                                    if (yData_avg_cur.get(yData_avg_cur.size() - 1) != 0) {
                                        ADC_Window.label_avg_cur.setText("<html>Средн значение тока<br />за последние " + time_of_avg_cur_in_seconds_out + " с: " + BigDecimal.valueOf(yData_avg_cur.get(yData_avg_cur.size() - 1)).setScale(2, RoundingMode.HALF_EVEN) + " A</html>");
                                    }
                                }
                                ADC_Window.label_max_volt.setText("<html>Макс значение напряжения<br />за весь период измерений: " + volt_max + " В</html>");
                                ADC_Window.label_min_volt.setText("<html>Мин значение напряжения<br />за весь период измерений: " + volt_min + " В</html>");
                            });
                        }
                    } catch (NullPointerException npe) {    // вылетало исключение при сработке фильтра помех
                        SwingUtilities.invokeLater(() -> {
                            StringWriter sw = new StringWriter();
                            npe.printStackTrace(new PrintWriter(sw));
                            ADC_Window.log.append(new Timestamp().out() + "Поймано NullPointerException в Measurement_Thread: " + sw.toString());
                        });
                        continue;
                    }

                    if (LocalDateTime.now().isAfter(end_meas_time.minusNanos(10000000))) {
                        yData_cur.add(0f);
                        yData_volt.add(0f);
                        yData_avg_cur.add(0f);
                        //yData_temp.add(yData_temp.get(yData_temp.size()-1));
                        xData.add(new Date());
                        SwingUtilities.invokeLater(() ->
                                ADC_Window.log.append(new Timestamp().out() + "Измерения завершены в связи с истекшим временем измерений\n")
                        );
                    }

                    if (LocalDateTime.now().isAfter(end_meas_time)) {
                        Thread.currentThread().interrupt();
                        SwingUtilities.invokeLater(() -> {
                            VBM.adc_window.progress_bar.setIndeterminate(false);           // выключаю прогрессбар
                            ADC_Window.button_start_measurements.setEnabled(true);          // включаю кнопку сразу после окончания измерений
                        });
                    }
                }
                while (!Thread.currentThread().isInterrupted() & !LocalDateTime.now().isAfter(end_meas_time));    // !!!!!!!!!!!!!! условие завершения цикла по установленному времени окончания !!!!!!!!!!
            }
        } catch (DateTimeParseException dtpe) {
            SwingUtilities.invokeLater(() ->
                    JOptionPane.showMessageDialog(VBM.adc_window, "Не выбрано время окончания измерений, либо введено в не верном формате", "Ошибка", JOptionPane.ERROR_MESSAGE)
            );
        } catch (InterruptedException intexc) {
            SwingUtilities.invokeLater(() ->
                    ADC_Window.log.append(new Timestamp().out() + "Поток измерений был превран во время Sleep\n")
            );
        }
    }
}

// класс отслеживания нажатия кнопки измерения REFP
class JButton_REFP_Listener implements ActionListener {
    public void actionPerformed(ActionEvent e) {
        REFP_Measurement_Thread refp_meas = new REFP_Measurement_Thread(ADC_Window.text_field_vvod_ip_address.getText());
        refp_meas.start();
    }
}

// класс отслеживания кнопки измерений
class JButton_Meas_Listener implements ActionListener {
    static ThreadGroup group_meas_threads = new ThreadGroup("Measurement_Threads");
    LocalDateTime get_start_time() {
        String yyyy_start = String.valueOf(ADC_Window.start_meas_time_List_of_years.getSelectedItem());
        String MM_start = String.valueOf(ADC_Window.start_meas_time_List_of_monthes.getSelectedItem());
        String dd_start = String.valueOf(ADC_Window.start_meas_time_List_of_days.getSelectedItem());
        String HH_start = String.valueOf(ADC_Window.start_meas_time_List_of_hours.getSelectedItem());
        String mm_start = String.valueOf(ADC_Window.start_meas_time_List_of_minutes.getSelectedItem());

        if (MM_start.length() == 1) {MM_start = 0 + MM_start;}
        if (dd_start.length() == 1) {dd_start = 0 + dd_start;}
        if (HH_start.length() == 1) {HH_start = 0 + HH_start;}
        if (mm_start.length() == 1) {mm_start = 0 + mm_start;}

        return LocalDateTime.parse((yyyy_start+MM_start+dd_start+HH_start+mm_start), DateTimeFormatter.ofPattern("yyyyMMddHHmm"));
    }
    public void actionPerformed(ActionEvent event) {

        if (ADC_Window.list_of_timeouts.getSelectedItem() != null) {

            String yyyy = String.valueOf(ADC_Window.end_meas_time_List_of_years.getSelectedItem());
            String MM = String.valueOf(ADC_Window.end_meas_time_List_of_monthes.getSelectedItem());
            String dd = String.valueOf(ADC_Window.end_meas_time_List_of_days.getSelectedItem());
            String HH = String.valueOf(ADC_Window.end_meas_time_List_of_hours.getSelectedItem());
            String mm = String.valueOf(ADC_Window.end_meas_time_List_of_minutes.getSelectedItem());

            if (MM.length() == 1) {MM = 0 + MM;}
            if (dd.length() == 1) {dd = 0 + dd;}
            if (HH.length() == 1) {HH = 0 + HH;}
            if (mm.length() == 1) {mm = 0 + mm;}

            LocalDateTime time_end_of_meas = LocalDateTime.parse((yyyy+MM+dd+HH+mm), DateTimeFormatter.ofPattern("yyyyMMddHHmm"));
            Measurements_Thread meas_thread = new Measurements_Thread(/*get_start_time(),*/ time_end_of_meas, ADC_Window.text_field_vvod_ip_address.getText(), Integer.parseInt(ADC_Window.text_field_vvod_avg_coefficient.getText()));

            if (LocalDateTime.now().isBefore(time_end_of_meas)) {
                if (!Measurements_Thread.yData_cur.isEmpty()) {
                    Measurements_Thread.yData_cur.add(0f);
                    Measurements_Thread.yData_volt.add(0f);
                    Measurements_Thread.yData_avg_cur.add(0f);
                    Measurements_Thread.xData.add(new Date());
                    if (!Measurements_Thread.yData_temp.isEmpty()) {
                        Measurements_Thread.yData_temp.add(Measurements_Thread.yData_temp.get(Measurements_Thread.yData_temp.size() - 1));
                    } else {
                        Measurements_Thread.yData_temp.add(0f);
                    }
                    Measurements_Thread.connection_time_array.add(0L);
                }
                //try {
                new Thread(group_meas_threads, meas_thread).start();
                //meas_thread.sleep(get_start_time().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli() - LocalDateTime.now().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli());
                SwingUtilities.invokeLater(() -> {
                    ADC_Window.log.append(new Timestamp().out() + "Цикл измерений запущен. Ожидаем заданного времени начала измерений\n");
                    ADC_Window.button_start_measurements.setEnabled(false);          // отключаю кнопку сразу после начала измерений
                });
                /*} catch (InterruptedException intexc) { 
                    SwingUtilities.invokeLater(() -> 
                            ADC_Window.log.append(new Timestamp().out() + "Поток измерений был превран во время Sleep\n") 
                    );}*/
            } else {SwingUtilities.invokeLater(() ->
                    JOptionPane.showMessageDialog(VBM.adc_window, "Установленное время окончания измерений меньше текущего", "Ошибка", JOptionPane.ERROR_MESSAGE));}
        } else {SwingUtilities.invokeLater(() ->
                JOptionPane.showMessageDialog(VBM.adc_window, "Не выбран таймаут соединения", "Ошибка", JOptionPane.ERROR_MESSAGE));}
    }
}

// класс отслеживания кнопки СТОП
class JButton_Stop_Meas_Listener implements ActionListener {
    public void actionPerformed(ActionEvent event) {
        try {
            JButton_Meas_Listener.group_meas_threads.interrupt();
            SwingUtilities.invokeLater(() ->
                    ADC_Window.button_start_measurements.setEnabled(true)
            );
            sleep(Measurements_Thread.connection_time*2);                                                            // иначе цикл измерений срабатывает еще 1 раз перед обнулением массивов
            Measurements_Thread.yData_cur.add(0f);
            Measurements_Thread.yData_volt.add(0f);
            Measurements_Thread.yData_shunt_volt.add(0f);
            Measurements_Thread.yData_avg_cur.add(0f);
            if (!Measurements_Thread.yData_temp.isEmpty()) {
                Measurements_Thread.yData_temp.add(Measurements_Thread.yData_temp.get(Measurements_Thread.yData_temp.size() - 1));
            } else {
                Measurements_Thread.yData_temp.add(0f);
            }
            Measurements_Thread.xData.add(new Date());
            Measurements_Thread.connection_time_array.add(0L);

            if (SwingUtilities.isEventDispatchThread()){
                ADC_Window.log.append(new Timestamp().out() + "Измерения остановлены вручную\n");
                VBM.adc_window.progress_bar.setIndeterminate(false);                                                  // выключаю прогрессбар
            } else {
                SwingUtilities.invokeLater(() -> {
                    ADC_Window.log.append(new Timestamp().out() + "Измерения остановлены вручную\n");
                    VBM.adc_window.progress_bar.setIndeterminate(false);                                                  // выключаю прогрессбар
                });
            }
        } catch (InterruptedException e)
        {SwingUtilities.invokeLater(() ->
                ADC_Window.log.append(new Timestamp().out() + "Поток измерений был превран во время Sleep\n")
        );
        }
    }
}

// класс открытия файла в отдельном потоке
class Add_Data_From_File extends Thread {
    private int i = 0;
    public void run() {
        try {
            JFileChooser fileChooser = new JFileChooser("C:");
            fileChooser.setDialogTitle("Открытие файла с массивами измерений");
            fileChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
            fileChooser.setFileFilter(new FileNameExtensionFilter("Файлы TXT", "txt"));

            Action details = fileChooser.getActionMap().get("viewTypeDetails");                                        // применяю по умолчанию вид таблицы (по факту происходит нажатие кнопки)
            details.actionPerformed(null);

            if (fileChooser.showOpenDialog(VBM.adc_window) == JFileChooser.APPROVE_OPTION) {
                if (EventQueue.isDispatchThread()) {
                    ADC_Window.log.append(new Timestamp().out() + "Открываем файл...\n");
                    ADC_Window.log.update(ADC_Window.log.getGraphics());                                               // иначе строка "Открываем файл" в логе появляется одновременно с "файл открыт"
                } else {
                    SwingUtilities.invokeLater(() -> {
                        ADC_Window.log.append(new Timestamp().out() + "Открываем файл...\n");
                        ADC_Window.log.update(ADC_Window.log.getGraphics());
                    });
                }
                String chosen_file_extension = fileChooser.getName(fileChooser.getSelectedFile());
                if (chosen_file_extension.substring(chosen_file_extension.lastIndexOf('.')).equals(".txt")) {

                    long lines_quantity = Files.lines(Paths.get(fileChooser.getSelectedFile().getPath()), StandardCharsets.UTF_8).count();     // подсчет количества строк в файле + прописал жестко кодировку

                    SwingUtilities.invokeLater(() ->
                            ADC_Window.log.append(new Timestamp().out() + "В открываемом файле найдено " + lines_quantity + " строк\n")
                    );

                    VBM.adc_window.progress_bar.setIndeterminate(false);
                    VBM.adc_window.progress_bar.setMaximum((int) lines_quantity);
                    VBM.adc_window.progress_bar.setMinimum(0);

                    FileReader file_reader = new FileReader(fileChooser.getSelectedFile(), StandardCharsets.UTF_8);
                    Scanner scanner = new Scanner(file_reader);

                    while (scanner.hasNextLine()) {
                        String line = scanner.nextLine();
                        if (!line.startsWith(" ") & !line.startsWith("[") & (line.length() > 1)) {
                            String[] line_splitted = line.split("\t");
                            if (!line_splitted[0].isEmpty()) {Measurements_Thread.yData_volt.add(Float.valueOf(line_splitted[0]));}
                            if (!line_splitted[1].isEmpty()) {Measurements_Thread.yData_cur.add(Float.valueOf(line_splitted[1]));}
                            if (!line_splitted[2].isEmpty()) {Measurements_Thread.yData_avg_cur.add(Float.valueOf(line_splitted[2]));}

                            SimpleDateFormat sdf = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss,SSS");
                            Date date = sdf.parse(line_splitted[3]);
                            Measurements_Thread.xData.add(date);

                            if (line_splitted.length > 5) {                                                            // в файлах старого типа было именно 5 колонок данных
                                Measurements_Thread.yData_temp.add(Float.valueOf(line_splitted[4]));
                                Measurements_Thread.connection_time_array.add(Long.valueOf(line_splitted[5]));
                            }
                        }
                        SwingUtilities.invokeLater(() -> {
                            i = ++i;
                            VBM.adc_window.progress_bar.setValue(i);
                            VBM.adc_window.progress_bar.repaint();                                                // работает и без этого, но так прогрессбар открывается плавнее
                        });
                    }
                    scanner.close();
                }
            }

            if (fileChooser.getSelectedFile() != null) {
                SimpleDateFormat sdf_out = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss,SSS");
                SwingUtilities.invokeLater(() ->
                        ADC_Window.log.append(new Timestamp().out() + "Данные из файла \"" + fileChooser.getSelectedFile().toString() + "\" c "
                                + sdf_out.format(Measurements_Thread.xData.get(0)) + " по " + sdf_out.format(Measurements_Thread.xData.get(Measurements_Thread.xData.size() - 1)) + " успешно загружены\n")
                );
            }
        } catch (IOException ioe) {
            SwingUtilities.invokeLater(() ->
                    ADC_Window.log.append(new Timestamp().out() + "Не возможно прочитать файл " + ioe.getMessage() + "\n")
            );
        } catch (ParseException pe) {
            SwingUtilities.invokeLater(() ->
                    ADC_Window.log.append(new Timestamp().out() + "Не верный формат даты в открываемом файле\n")
            );
        } catch (NumberFormatException nfe) {
            SwingUtilities.invokeLater(() -> {
                ADC_Window.log.append(new Timestamp().out() + "Не верный формат значений тока/напряжения в открываемом файле (" + nfe.getMessage() + ")\n");
                StringWriter sw = new StringWriter();
                nfe.printStackTrace(new PrintWriter(sw));
                ADC_Window.log.append(new Timestamp().out() + sw.toString() + "\n");
            });
        }
    }
}

// класс сохранения файла в отдельном потоке
class Save_Data_To_File extends Thread {
    private File file;
    private int j = 1;     // счетчик для прогрессбара
    public void run() {
        try {
            UIManager.put("FileChooser.saveButtonText", "Сохранить");

            JFileChooser fileChooser = new JFileChooser("C:");
            fileChooser.setDialogTitle("Сохранение массивов в файл");
            fileChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
            fileChooser.setFileFilter(new FileNameExtensionFilter("Файлы TXT", "txt"));

            Action details = fileChooser.getActionMap().get("viewTypeDetails");                                        // применяю по умолчанию вид таблицы (по факту происходит нажатие кнопки)
            details.actionPerformed(null);

            if (fileChooser.showSaveDialog(VBM.adc_window) == JFileChooser.APPROVE_OPTION) {
                file = fileChooser.getSelectedFile();

                String chosen_file_name = file.getName();
                if (chosen_file_name.contains(".")) {
                    if (!chosen_file_name.substring(chosen_file_name.lastIndexOf('.')).equals(".txt")) {
                        file = new File(file.toString() + ".txt");
                    }
                } else {
                    file = new File(file.toString() + ".txt");
                }

                if (EventQueue.isDispatchThread()) {
                    ADC_Window.log.append(new Timestamp().out() + "Сохраняем массивы в файл...\n");
                    ADC_Window.log.update(ADC_Window.log.getGraphics());
                    VBM.adc_window.progress_bar.setIndeterminate(false);
                    VBM.adc_window.progress_bar.setMaximum(Measurements_Thread.yData_cur.size());
                    VBM.adc_window.progress_bar.setMinimum(0);
                } else {
                    SwingUtilities.invokeLater(() ->  {
                                ADC_Window.log.append(new Timestamp().out() + "Сохраняем массивы в файл...\n");
                                ADC_Window.log.update(ADC_Window.log.getGraphics());
                                VBM.adc_window.progress_bar.setIndeterminate(false);
                                VBM.adc_window.progress_bar.setMaximum(Measurements_Thread.yData_cur.size());
                                VBM.adc_window.progress_bar.setMinimum(0);
                            }
                    );
                }

                FileWriter file_writer = new FileWriter(file, StandardCharsets.UTF_8, true);                  // добавил записть строго в кодировке UTF-8, иначе использовалась кодировку по умолчанию и при чтении могли возникать ошибки
                file_writer.write(" Размер массива напряжения: " + Measurements_Thread.yData_volt.size() + "\r\n");
                file_writer.write(" Размер массива мгн тока: " + Measurements_Thread.yData_cur.size() + "\r\n");
                file_writer.write(" Размер массива среднего тока (темп компенс): " + Measurements_Thread.yData_avg_cur.size() + "\r\n");
                file_writer.write(" Размер массива температуры: " + Measurements_Thread.yData_temp.size() + "\r\n");
                file_writer.write(" Размер массива времени ответа: " + Measurements_Thread.connection_time_array.size() + "\r\n");
                file_writer.write(" ------------------------------------------------------------------------------------------------------------" + "\r\n");
                file_writer.write(" " + ADC_Window.label_time_of_meas.getText() + "\r\n");
                file_writer.write(" " + ADC_Window.label_cycle_counter.getText() + "\r\n");
                file_writer.write(" " + ADC_Window.label_timeout_counter.getText() + "\r\n");
                file_writer.write(" " + ADC_Window.label_watt_hour.getText() + "\r\n");
                file_writer.write(" " + ADC_Window.label_watt_hour_avg.getText() + "\r\n");
                file_writer.write(" " + ADC_Window.label_min_volt.getText() + "\r\n");
                file_writer.write(" " + ADC_Window.label_max_volt.getText() + "\r\n");
                file_writer.write(" " + ADC_Window.label_min_cur.getText() + "\r\n");
                file_writer.write(" " + ADC_Window.label_max_cur.getText() + "\r\n");
                file_writer.write(" ------------------------------------------------------------------------------------------------------------" + "\r\n");
                file_writer.write(ADC_Window.log.getText() + "\r\n");
                file_writer.write(" ------------------------------------------------------------------------------------------------------------" + "\r\n");
                file_writer.write(" Мгн напр, В" + "\t"
                        + "Мгн ток, А" + "\t"
                        + "Средн ток (темп компенс), А" + "\t"
                        + "Дата и время измерения" + "\t"
                        + "Температура платы, \u00b0С" + "\t"
                        + "Время ответа, мс" + "\r\n");
                file_writer.write(" ------------------------------------------------------------------------------------------------------------" + "\r\n");

                SimpleDateFormat sdf = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss,SSS");
                for (int i = 0; i < Measurements_Thread.yData_cur.size(); i++) {
                    file_writer.write(Measurements_Thread.yData_volt.get(i) + "\t"
                            + Measurements_Thread.yData_cur.get(i) + "\t"
                            + Measurements_Thread.yData_avg_cur.get(i) + "\t"
                            + sdf.format(Measurements_Thread.xData.get(i)) + "\t"
                            + Measurements_Thread.yData_temp.get(i) + "\t"
                            + Measurements_Thread.connection_time_array.get(i) + "\r\n");
                    file_writer.flush();
                    j = ++j;
                    SwingUtilities.invokeLater(() -> {
                                VBM.adc_window.progress_bar.setValue(j);
                                VBM.adc_window.progress_bar.repaint();
                            }
                    );
                }
                file_writer.close();
                SwingUtilities.invokeLater(() -> {
                            ADC_Window.log.append(new Timestamp().out() + "Файл " + file.toString() + " сохранен\n");
                            if (JButton_Meas_Listener.group_meas_threads.activeCount() > 0) {VBM.adc_window.progress_bar.setIndeterminate(true);}
                        }
                );
            }
        } catch (IOException ioe) {
            SwingUtilities.invokeLater(() ->
                    ADC_Window.log.append(new Timestamp().out() + "Не возможно сохранить файл " + ioe.getMessage() + "\n")
            );
        } catch (StringIndexOutOfBoundsException io) {
            SwingUtilities.invokeLater(() ->
                    JOptionPane.showMessageDialog(VBM.adc_window, "Выбранный файл не имеет расширения (данное сообщение никогда не должно показываться)", "ОШИБКА", JOptionPane.ERROR_MESSAGE)     // никогда не должно срабатывать
            );
        }
    }
}

// класс лисенера открытия файла
class Open_File_Listener implements ActionListener {
    @Override
    public void actionPerformed(ActionEvent e) {
        if (JButton_Meas_Listener.group_meas_threads.activeCount() == 0) {
            if (Measurements_Thread.yData_cur.isEmpty()) {
                new Add_Data_From_File().start();
            } else {
                String[] options = {"Удалить текущие данные", "Добавить данные из файла к текущим", "Сохранить текущие данные в файл"};
                SwingUtilities.invokeLater(() -> {
                            int n = JOptionPane.showOptionDialog(VBM.adc_window, "Массивы уже содержат данные измерений, удалить их?",
                                    "Обнаружены данные в памяти программы", JOptionPane.DEFAULT_OPTION, JOptionPane.INFORMATION_MESSAGE, null, options, options[2]);
                            if (n == 0) {
                                Measurements_Thread.yData_cur.clear();
                                Measurements_Thread.yData_avg_cur.clear();
                                Measurements_Thread.yData_volt.clear();
                                Measurements_Thread.xData.clear();
                                Measurements_Thread.yData_temp.clear();
                                ADC_Window.log.append(new Timestamp().out() + "Массивы данных измерений удалены\n");
                                new Add_Data_From_File().start();
                            }
                            if (n == 1) {
                                new Add_Data_From_File().start();
                            }
                            if (n == 2) {
                                new Save_File_Listener().actionPerformed(e);
                            }
                        }
                );
            }
        } else {SwingUtilities.invokeLater(() ->
                JOptionPane.showMessageDialog(VBM.adc_window, "Не возможно открыть файл при запущенных измерениях", "Ошибка", JOptionPane.ERROR_MESSAGE)
        );}
    }
}

// класс сохранения файла
class Save_File_Listener implements ActionListener  {
    @Override
    public void actionPerformed(ActionEvent e) {
        new Save_Data_To_File().start();
    }
}

public class VBM {

    static ADC_Window adc_window = new ADC_Window();                                             // создаем экземпляр класса отрисовки окна
    final static Object sync = new Object();                                                     //************** объект для синхронизации потоков Meas и Graph*****************

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
                    adc_window.setVisible(true); // показываем окно
                }
        );

        setDefaultUncaughtExceptionHandler((thread, eh) ->                                       // отлавливаем и направляем в лог все необработанные исключения
                {
                    StringWriter sw = new StringWriter();
                    eh.printStackTrace(new PrintWriter(sw));
                    ADC_Window.log.append(new Timestamp().out() + sw.toString() + "\n");
                }
        );
    }
}