package net.flamgop;

import com.formdev.flatlaf.FlatDarkLaf;
import net.flamgop.adb.ADBUtil;
import net.flamgop.util.Pair;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
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

            AtomicReference<Path> adbPath = new AtomicReference<>();
            AtomicReference<Process> processRef = new AtomicReference<>();

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
            terminal.setFont(new Font("Monospaced", Font.PLAIN, 12));

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

            // Center button with its own constraints
            JButton centerButton = new JButton("Start Logging");
            GridBagConstraints centerConstraints = new GridBagConstraints();
            centerConstraints.anchor = GridBagConstraints.NORTH;
            centerConstraints.gridx = 1;
            centerConstraints.gridy = 0;
            centerConstraints.weightx = 1.0;
            centerConstraints.fill = GridBagConstraints.HORIZONTAL;
            centerConstraints.insets = new Insets(0, 5, 5, 5);
            centerButton.setEnabled(false);
            buttonPanel.add(centerButton, centerConstraints);
            centerButton.addActionListener(e -> {
                if (adbPath.get() != null && processRef.get() == null) {
                    try {
                        Pair<CompletableFuture<Void>, Process> pair = ADBUtil.logcat(adbPath.get().toString(), "com.qcxr.qcxr", proxyStream);
                        pair.a().whenComplete((_, thr) -> {
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
                                textAreaStream.write("\nDone".getBytes(StandardCharsets.UTF_8));
                                Toolkit.getDefaultToolkit().beep();
                            } catch (IOException ex) {
                                throw new RuntimeException(ex);
                            }
                        });
                        processRef.set(pair.b());
                        textAreaStream.write("Started logging...\n".getBytes(StandardCharsets.UTF_8));
                        Toolkit.getDefaultToolkit().beep();
                        centerButton.setText("Stop Logging");
                    } catch (IOException ex) {
                        throw new RuntimeException(ex);
                    }
                } else if (processRef.get() != null) {
                    processRef.get().destroy();
                    processRef.set(null);
                    try {
                        textAreaStream.write("\nStopped logging...\n".getBytes(StandardCharsets.UTF_8));
                        Toolkit.getDefaultToolkit().beep();
                    } catch (IOException ex) {
                        throw new RuntimeException(ex);
                    }
                    centerButton.setText("Start Logging");
                }
            });

            JButton leftButton = new JButton("Update ADB");
            GridBagConstraints leftConstraints = new GridBagConstraints();
            leftConstraints.anchor = GridBagConstraints.NORTHWEST;
            leftConstraints.gridx = 0;
            leftConstraints.gridy = 0;
            leftConstraints.weightx = 1.0;
            leftConstraints.fill = GridBagConstraints.HORIZONTAL;
            leftConstraints.insets = new Insets(0, 0, 5, 5); // Margin around buttons
            buttonPanel.add(leftButton, leftConstraints);
            leftButton.addActionListener(e -> {
                leftButton.setEnabled(false);
                centerButton.setEnabled(false);
                adbPath.set(ADBUtil.getAdbPath());
                try {
                    textAreaStream.write("Done!\n".getBytes(StandardCharsets.UTF_8));
                    leftButton.setEnabled(true);
                    centerButton.setEnabled(true);
                    Toolkit.getDefaultToolkit().beep();
                } catch (IOException ex) {
                    throw new RuntimeException(ex);
                }
            });

            // Right button with its own constraints
            JButton rightButton = new JButton("Save Log");
            GridBagConstraints rightConstraints = new GridBagConstraints();
            rightConstraints.anchor = GridBagConstraints.NORTHEAST;
            rightConstraints.gridx = 2;
            rightConstraints.gridy = 0;
            rightConstraints.weightx = 1.0;
            rightConstraints.fill = GridBagConstraints.HORIZONTAL;
            rightConstraints.insets = new Insets(0, 5, 5, 0);
            buttonPanel.add(rightButton, rightConstraints);
            rightButton.addActionListener(e -> {
                rightButton.setEnabled(false);
                try {
                    streamRef.get().close();
                    streamRef.set(null);
                    FileDialog fileDialog = new FileDialog(frame, "Choose a save location", FileDialog.SAVE);
                    fileDialog.setDirectory(new File("./").getAbsolutePath());
                    fileDialog.setFile("log.txt");
                    fileDialog.setFilenameFilter((_, name) -> name.endsWith(".txt"));
                    fileDialog.addNotify();
                    fileDialog.setVisible(true);
                    String fileName = fileDialog.getFile();
                    if (fileName != null) {
                        File file = new File(fileName);
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
            });

            frame.setLayout(new BorderLayout(4, 4));
            topPanel.setLayout(new BorderLayout(0,0 ));
            topPanel.add(scrollPane, BorderLayout.NORTH);
            topPanel.add(buttonPanel, BorderLayout.SOUTH);
            frame.add(topPanel, BorderLayout.NORTH);

            frame.setVisible(true);
        });
    }
}