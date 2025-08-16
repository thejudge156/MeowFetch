package net.flamgop;

import com.formdev.flatlaf.FlatDarkLaf;
import dadb.AdbShellStream;
import dadb.Dadb;
import net.flamgop.adb.ADBUtil;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

public class Main {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            FlatDarkLaf.setup();

            AtomicReference<CompletableFuture<Void>> future = new AtomicReference<>();

            JFrame frame = new JFrame("MeowFetch");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setSize(960, 540);

            try {
                frame.setIconImage(ImageIO.read(Objects.requireNonNull(Main.class.getResourceAsStream("/icon.png"))));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }

            frame.setLayout(new BorderLayout());

            JPanel topPanel = new JPanel();

            JTextArea terminal = new JTextArea();
            terminal.setEditable(false);
            terminal.setLineWrap(false);
            terminal.setBackground(Color.BLACK);
            terminal.setForeground(Color.WHITE);
            terminal.setDisabledTextColor(Color.WHITE);
            terminal.setFont(new Font("Monospaced", Font.PLAIN, 12));
            terminal.setEnabled(false);

            OutputStream textAreaStream = new OutputStream() {
                @Override
                public void write(int b) {
                    terminal.append(String.valueOf((char) b));
                }
            };

            File logOutput;
            try {
                logOutput = File.createTempFile("com.qcxr.qcxr.log", ".txt");
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            AtomicReference<FileOutputStream> streamRef = new AtomicReference<>();
            try {
                streamRef.set(new FileOutputStream(logOutput));
            } catch (FileNotFoundException e) {
                throw new RuntimeException(e);
            }
            OutputStream proxyStream = new OutputStream() {
                @Override
                public void write(int b) throws IOException {
                    textAreaStream.write(b);
                    if (streamRef.get() != null) streamRef.get().write(b);
                }

                @Override
                public void close() throws IOException {
                    super.close();
                    if (streamRef.get() != null) streamRef.get().close();
                }
            };

            JScrollPane scrollPane = new JScrollPane(terminal);
            scrollPane.setPreferredSize(new Dimension(960, 540/2));

            JPanel buttonPanel = new JPanel(new GridBagLayout());

            JButton centerButton = new JButton("Start Logging");
            GridBagConstraints centerConstraints = createGridConstraints(
                GridBagConstraints.NORTH,
                GridBagConstraints.HORIZONTAL,
                1, 0,
                new Insets(0, 5, 5, 5)
            );
            centerButton.setEnabled(false);
            buttonPanel.add(centerButton, centerConstraints);
            centerButton.setPreferredSize(new Dimension(960 / 6, 22));

            JButton leftButton = new JButton("Init ADB (click me first!)");
            GridBagConstraints leftConstraints = createGridConstraints(
                GridBagConstraints.NORTHWEST,
                GridBagConstraints.HORIZONTAL,
                0, 0,
                new Insets(0, 0, 5, 5)
            );
            buttonPanel.add(leftButton, leftConstraints);
            leftButton.setPreferredSize(new Dimension(960 / 6, 22));

            JPanel logLevelPanel = new JPanel();
            JLabel label = new JLabel("Log Level: ");
            logLevelPanel.add(label);

            JComboBox<LoggingLevel> comboBox = new JComboBox<>(LoggingLevel.values());
            logLevelPanel.add(comboBox);
            comboBox.setEnabled(false);
            comboBox.setSelectedIndex(1);

            GridBagConstraints loggingLevelConstraints = createGridConstraints(
                GridBagConstraints.NORTHWEST,
                GridBagConstraints.NONE,
                0, 1,
                new Insets(0, 2, 5, 5)
            );
            buttonPanel.add(logLevelPanel, loggingLevelConstraints);

            JPanel logFilterPanel = new JPanel(new GridBagLayout());
            JLabel label2 = new JLabel("Log Filter: ");
            label2.setPreferredSize(new Dimension(label2.getFontMetrics(label2.getFont()).stringWidth("Log Filter: "), 22));
            logFilterPanel.add(label2, createGridConstraints(GridBagConstraints.WEST, GridBagConstraints.HORIZONTAL, 0, 0, new Insets(4, 5, 0, 0)));

            JTextField textField = new JTextField();
            logFilterPanel.add(textField, createGridConstraints(GridBagConstraints.WEST, GridBagConstraints.HORIZONTAL, 0, 0, new Insets(4, label2.getFontMetrics(label2.getFont()).stringWidth("Log Filter: ") + 10, 0, 0)));
            textField.setText("com.qcxr.qcxr");
            textField.setEnabled(false);

            GridBagConstraints loggingFilterConstraints = createGridConstraints(
                GridBagConstraints.NORTHWEST,
                GridBagConstraints.HORIZONTAL,
                1, 1,
                new Insets(0, 0, 5, 5)
            );
            buttonPanel.add(logFilterPanel, loggingFilterConstraints);

            // Right button with its own constraints
            JButton rightButton = new JButton("Save Log");
            GridBagConstraints rightConstraints = createGridConstraints(
                GridBagConstraints.NORTHEAST,
                GridBagConstraints.HORIZONTAL,
                2, 0,
                new Insets(0, 5, 5, 0)
            );
            rightButton.setEnabled(false);
            buttonPanel.add(rightButton, rightConstraints);
            rightButton.setPreferredSize(new Dimension(960 / 6, 22));

            leftButton.addActionListener(ignored -> {
                try {
                    ADBUtil.initADB();
                } catch (IllegalStateException e) {
                    try {
                        // ew
                        textAreaStream.write("No device detected! Is there a prompt on your headset?\n".getBytes(StandardCharsets.UTF_8));
                    } catch (IOException e1) {
                        throw new RuntimeException(e1);
                    }
                    return;
                }
                leftButton.setEnabled(false);
                centerButton.setEnabled(false);
                centerButton.setEnabled(false);
                comboBox.setEnabled(false);
                textField.setEnabled(false);
                try {
                    textAreaStream.write("Done!\n".getBytes(StandardCharsets.UTF_8));
                    leftButton.setText("Reinit ADB");
                    leftButton.setEnabled(true);
                    centerButton.setEnabled(true);
                    rightButton.setEnabled(true);
                    comboBox.setEnabled(true);
                    textField.setEnabled(true);
                    Toolkit.getDefaultToolkit().beep();
                } catch (IOException ex) {
                    throw new RuntimeException(ex);
                }
            });
            centerButton.addActionListener(ignored -> {
                if (future.get() == null) {
                    comboBox.setEnabled(false);
                    rightButton.setEnabled(false);
                    leftButton.setEnabled(false);
                    textField.setEnabled(false);
                    try {
                        CompletableFuture<Void> pair = ADBUtil.logcat(comboBox.getItemAt(comboBox.getSelectedIndex()), textField.getText(), proxyStream);
                        pair.whenComplete((ignored1, thr) -> {
                            if (thr != null) {
                                try {
                                    textAreaStream.write("\n".getBytes(StandardCharsets.UTF_8));
                                    textAreaStream.write(thr.toString().getBytes(StandardCharsets.UTF_8));
                                    Toolkit.getDefaultToolkit().beep();
                                } catch (IOException ex) {
                                    throw new RuntimeException(ex);
                                }
                            }
                            try {
                                comboBox.setEnabled(true);
                                leftButton.setEnabled(true);
                                rightButton.setEnabled(true);
                                textField.setEnabled(true);
                                textAreaStream.write("\nDone".getBytes(StandardCharsets.UTF_8));
                                Toolkit.getDefaultToolkit().beep();
                            } catch (IOException ex) {
                                throw new RuntimeException(ex);
                            }
                        });
                        future.set(pair);
                        textAreaStream.write("Started logging...\n".getBytes(StandardCharsets.UTF_8));
                        Toolkit.getDefaultToolkit().beep();
                        centerButton.setText("Stop Logging");
                    } catch (IOException ex) {
                        throw new RuntimeException(ex);
                    }
                } else if (future.get() != null) {
                    future.set(null);
                    ADBUtil.stream.ifPresent(AdbShellStream::close);
                    try {
                        comboBox.setEnabled(true);
                        leftButton.setEnabled(true);
                        rightButton.setEnabled(true);
                        textField.setEnabled(true);
                        textAreaStream.write("\nStopped logging\n".getBytes(StandardCharsets.UTF_8));
                        Toolkit.getDefaultToolkit().beep();
                    } catch (IOException ex) {
                        throw new RuntimeException(ex);
                    }
                    centerButton.setText("Start Logging");
                }
            });
            rightButton.addActionListener(ignored -> {
                rightButton.setEnabled(false);
                centerButton.setEnabled(false);
                leftButton.setEnabled(false);
                comboBox.setEnabled(false);
                textField.setEnabled(false);
                try {
                    streamRef.get().close();
                    streamRef.set(null);
                    FileDialog fileDialog = new FileDialog(frame, "Choose a save location", FileDialog.SAVE);
                    fileDialog.setDirectory(new File("./").getAbsolutePath());
                    fileDialog.setFile("log.txt");
                    fileDialog.setFilenameFilter((ignored1, name) -> name.endsWith(".txt"));
                    fileDialog.addNotify();
                    fileDialog.setVisible(true);
                    String fileName = fileDialog.getFile();
                    if (fileName != null) {
                        File file = new File(fileName);
                        //noinspection ResultOfMethodCallIgnored
                        file.createNewFile();
                        try (FileInputStream fis = new FileInputStream(logOutput)) {
                            try (FileOutputStream fos = new FileOutputStream(file)) {
                                fos.write(fis.readAllBytes());
                            }
                        } catch (IOException ex) {
                            throw new RuntimeException(ex);
                        }
                        JOptionPane.showMessageDialog(frame, "Saved file at " + file.getAbsolutePath() + "!");
                    }
                    streamRef.set(new FileOutputStream(logOutput));
                } catch (IOException ex) {
                    throw new RuntimeException(ex);
                }
                rightButton.setEnabled(true);
                centerButton.setEnabled(true);
                leftButton.setEnabled(true);
                comboBox.setEnabled(true);
                textField.setEnabled(true);
            });

            frame.setLayout(new BorderLayout(4, 4));
            topPanel.setLayout(new BorderLayout(0,0 ));
            topPanel.add(scrollPane, BorderLayout.NORTH);
            topPanel.add(buttonPanel, BorderLayout.SOUTH);
            frame.add(topPanel, BorderLayout.NORTH);

            Thread thread = new Thread(() -> {
                while(true) {
                    terminal.moveCaretPosition(terminal.getDocument().getLength());
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException ex) {
                        // whatev
                    }
                }
            });
            thread.start();

            leftButton.requestFocus(FocusEvent.Cause.MOUSE_EVENT);
            frame.setVisible(true);
        });
    }

    private static GridBagConstraints createGridConstraints(int anchor, int fill, int x, int y, Insets insets) {
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.anchor = anchor;
        constraints.gridx = x;
        constraints.gridy = y;
        constraints.weightx = 1.0;
        constraints.weighty = 1.0;
        constraints.fill = fill;
        constraints.insets = insets;
        return constraints;
    }
}