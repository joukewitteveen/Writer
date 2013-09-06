// This MIDlet requires JSRs 75, 118, 139

import java.io.*;
import java.util.Enumeration;

import javax.microedition.io.Connector;
import javax.microedition.io.file.FileConnection;
import javax.microedition.io.file.FileSystemRegistry;
import javax.microedition.lcdui.*;
import javax.microedition.midlet.*;
import javax.microedition.rms.*;


public class Writer extends MIDlet implements CommandListener {
	public static final int MAX_SIZE = 0x100000;
	public static final Command readCommand = new Command("Read...", Command.ITEM, 1),
			writeCommand = new Command("Write...", Command.ITEM, 2),
			clearCommand = new Command("Clear", Command.ITEM, 3),
			rootCommand = new Command("Select Root...", Command.SCREEN, 10),
			exitCommand = new Command("Exit", Command.EXIT, 20);
	Display display;
	TextBox text = new TextBox("<New File>", null, MAX_SIZE, TextField.ANY);
	Settings settings = new Settings();
	private boolean dialog;
	private int savedHash;
	class Settings {
		public static final int ROOT = 1, PATH = 2, FILE = 3;
		private static final String SETTINGS_STORE = "Settings";
		private RecordStore store;

		public Settings() {
			try {
				store = RecordStore.openRecordStore(SETTINGS_STORE, false);
				if(store.getNumRecords() != 3 || store.getNextRecordID() != 4) {
					throw new RecordStoreException("Malformed settings store");
				}
			} catch (RecordStoreException e) {
				try {
					if(store != null) {
						store.closeRecordStore();
						RecordStore.deleteRecordStore(SETTINGS_STORE);
					}
					store = RecordStore.openRecordStore(SETTINGS_STORE, true);
					for(int i = ROOT; i <= FILE; i++) {
						store.addRecord(null, 0, 0);
					}
				} catch (RecordStoreException f) {
					notifyDestroyed();
				}
			}
		}

		public String get(int id) {
			byte[] bytes;
			try {
				bytes = store.getRecord(id);
			} catch (RecordStoreException e) {
				return null;
			}
			if(bytes == null) {
				return new String();
			}
			return new String(bytes);
		}

		public boolean set(int id, String value) {
			byte[] bytes = value.getBytes();
			try {
				store.setRecord(id, bytes, 0, bytes.length);
			} catch (RecordStoreException e) {
				return false;
			}
			return true;
		}
	}

	public Writer() {
		display = Display.getDisplay(this);
		text.addCommand(readCommand);
		text.addCommand(writeCommand);
		text.addCommand(clearCommand);
		text.addCommand(rootCommand);
		text.addCommand(exitCommand);
		text.setCommandListener(this);
	}

	private boolean isModified() {
		return text.getString().hashCode() != savedHash;
	}

	private boolean verifyIntent(Command command) {
		if(dialog) {
			display.setCurrent(text);
			dialog = false;
			return true;
		} else if(!isModified()) {
			return true;
		}
		display.setCurrent(new YesNoDialog(this, command, "The content was modified", "Are you sure?"));
		dialog = true;
		return false;
	}

	void displayFile(String path, String file) {
		savedHash = text.getString().hashCode();
		settings.set(Settings.PATH, path);
		settings.set(Settings.FILE, file);
		text.setTitle(file);
		display.setCurrent(text);
	}

	public void read(InputStream source, int length) {
		int size = Math.min(length, text.getMaxSize());
		char[] cbuf = new char[size];
		try {
			InputStreamReader reader = new InputStreamReader(source);
			reader.read(cbuf, 0, size);
			reader.close();
			text.setChars(cbuf, 0, size);
		} catch (IOException e) {
			display.setCurrent(new Alert("Reading failed", e.getMessage(), null, AlertType.ERROR));
		}
	}

	public void write(OutputStream target) {
		try {
			OutputStreamWriter writer = new OutputStreamWriter(target);
			writer.write(text.getString());
			writer.close();
		} catch (IOException e) {
			display.setCurrent(new Alert("Writing failed", e.getMessage(), null, AlertType.ERROR));
		}
	}

	protected void startApp() throws MIDletStateChangeException {
		if(settings.get(Settings.ROOT).length() == 0) {
			display.setCurrent(new RootList(this));
		} else {
			display.setCurrent(text);
		}
	}

	protected void pauseApp() {
	}

	protected void destroyApp(boolean unconditional) throws MIDletStateChangeException {
		if(!unconditional && isModified()) {
			display.setCurrent(new Alert("The content was modified", "Exit prevented.", null, AlertType.INFO));
			throw new MIDletStateChangeException();
		}
	}

	public void commandAction(Command command, Displayable displayable) {
		if(command == writeCommand) {
			display.setCurrent(new FileBrowser(this, true));
		} else if(command == rootCommand) {
			display.setCurrent(new RootList(this));
		} else if(command == YesNoDialog.cancelCommand) {
			display.setCurrent(text);
			dialog = false;
		} else if(!verifyIntent(command)) {
			return;
		} else if(command == readCommand) {
			display.setCurrent(new FileBrowser(this, false));
		} else if(command == clearCommand) {
			text.setString("");
		} else if(command == exitCommand) {
			notifyDestroyed();
		}
	}
}


class YesNoDialog extends Alert implements CommandListener {
	public static final Command cancelCommand = new Command("No", Command.CANCEL, 1),
			okCommand = new Command("Yes", Command.OK, 2);
	private CommandListener commandListener;
	private Command yesCommand;

	public YesNoDialog(CommandListener listener, Command yesCommand, String title, String alertText) {
		super(title, alertText, null, null);
		this.commandListener = listener;
		this.yesCommand = yesCommand;
		addCommand(cancelCommand);
		addCommand(okCommand);
		setCommandListener(this);
	}

	public void commandAction(Command command, Displayable dialog) {
		if(command == okCommand) {
			command = yesCommand;
		}
		commandListener.commandAction(command, dialog);
	}
}


class RootList extends List implements CommandListener {
	private static final Command cancelCommand = new Command("Cancel", Command.CANCEL, 1),
			okCommand = new Command("OK", Command.OK, 2);
	private final Writer parent;

	public RootList(Writer parent) {
		super("Select Root...", Choice.IMPLICIT);
		this.parent = parent;
		String preferredRoot = parent.settings.get(Writer.Settings.ROOT);
		Enumeration roots = FileSystemRegistry.listRoots();
		while(roots.hasMoreElements()) {
			String root = (String) roots.nextElement();
			append(root, null);
			if(root.equals(preferredRoot)) {
				setSelectedIndex(size() - 1, true);
			}
		}
		addCommand(cancelCommand);
		addCommand(okCommand);
		setCommandListener(this);
	}

	public void commandAction(Command command, Displayable displayable) {
		if(command == List.SELECT_COMMAND || command == okCommand) {
			List list = (List) displayable;
			parent.settings.set(Writer.Settings.ROOT, list.getString(list.getSelectedIndex()));
		}
		parent.display.setCurrent(parent.text);
	}
}


class FileBrowser extends List implements CommandListener {
	public static final Command cancelCommand = new Command("Cancel", Command.CANCEL, 1),
			okCommand = new Command("OK", Command.OK, 2);
	public static final String NEW_FILE = "<New File...>";
	private static final Command writeCommand = new Command("Write", Command.ITEM, 3);
	private final boolean write;
	private final Writer parent;
	private final String root;
	private FileConnection fconn;
	private class FileName extends Form implements CommandListener {
		public FileName() {
			super("New File...", new Item[] {new TextField("File Name", "", 0xFF, TextField.ANY)});
			addCommand(cancelCommand);
			addCommand(okCommand);
			setCommandListener(this);
		}

		public void commandAction(Command command, Displayable form) {
			FileBrowser browser = FileBrowser.this;
			if(command == okCommand) {
				String file = ((TextField) get(0)).getString();
				try {
					FileConnection newFile = (FileConnection) Connector.open(fconn.getURL() + file);
					if(newFile.exists()) {
						newFile.close();
						browser.set(browser.getSelectedIndex(), file, null);
						parent.display.setCurrent(browser);
					} else {
						newFile.create();
						fconn.close();
						fconn = newFile;
						command = writeCommand;
					}
					browser.commandAction(command, form);
				} catch (IOException e) {
					parent.display.setCurrent(new Alert("Could not create file", e.getMessage(), null, AlertType.ERROR));
				}
			} else {
				browser.parent.display.setCurrent(browser);
			}
		}
	}

	public FileBrowser(Writer parent, boolean write) {
		super(write ? "Write..." : "Read...", Choice.IMPLICIT);
		this.write = write;
		this.parent = parent;
		this.root = "/" + parent.settings.get(Writer.Settings.ROOT);
		String path = parent.settings.get(Writer.Settings.PATH), file = "";
		try {
			fconn = (FileConnection) Connector.open("file://" + root + path);
			if(!fconn.isDirectory()) {
				throw new IOException("Not a directory");
			}
			file = parent.settings.get(Writer.Settings.FILE);
		} catch (IOException e) {
			try {
				fconn = (FileConnection) Connector.open("file://" + root);
			} catch (IOException f) {
				parent.display.setCurrent(new Alert("Could not open root", f.getMessage(), null, AlertType.ERROR));
			}
		}
		refresh();
		if(file.length() > 0) {
			for(int i = 0; i < size(); i++) {
				if(getString(i).equals(file)){
					setSelectedIndex(i, true);
					break;
				}
			}
		}
		addCommand(cancelCommand);
		addCommand(okCommand);
		setCommandListener(this);
	}

	public void refresh() {
		deleteAll();
		if(fconn.getName().length() != 0) {
			append("..", null);
		}
		try {
			Enumeration ls = fconn.list("*", true);
			while(ls.hasMoreElements()) {
				append((String) ls.nextElement(), null);
			}
		} catch (IOException e) {
			parent.display.setCurrent(new Alert("Could not list directory", e.getMessage(), null, AlertType.ERROR));
		}
		if(write) {
			append(NEW_FILE, null);
		}
	}

	public void commandAction(Command command, Displayable form) {
		if(command == List.SELECT_COMMAND || command == okCommand) {
			String file = getString(getSelectedIndex());
			if(file.equals(NEW_FILE)) {
				parent.display.setCurrent(new FileName());
				return;
			}
			try {
				fconn.setFileConnection(file);
			} catch (IOException e) {
				parent.display.setCurrent(new Alert("Could not open file", e.getMessage(), null, AlertType.ERROR));
				return;
			}
			if(fconn.isDirectory()) {
				refresh();
				return;
			}
			try {
				if(write) {
					parent.display.setCurrent(new YesNoDialog(this, writeCommand, "The file exists", "Do you want to overwrite the file?"));
					return;
				} else {
					parent.read(fconn.openInputStream(), (int) fconn.fileSize());
					parent.displayFile(fconn.getPath().substring(root.length()), file);
				}
			} catch (IOException e) {
				parent.display.setCurrent(new Alert("Could not open stream", e.getMessage(), null, AlertType.ERROR));
				return;
			}
		} else if(command == writeCommand) {
			try {
				fconn.truncate(parent.text.size());
				parent.write(fconn.openOutputStream());
				parent.displayFile(fconn.getPath().substring(root.length()), fconn.getName());
			} catch (IOException e) {
				parent.display.setCurrent(new Alert("Could not open stream", e.getMessage(), null, AlertType.ERROR));
			}
		} else if(command == cancelCommand) {
			parent.display.setCurrent(parent.text);
		} else if(command == YesNoDialog.cancelCommand) {
			parent.display.setCurrent(this);
			return;
		}
		try {
			fconn.close();
		} catch (IOException e) {
			// No consequence
		}
	}
}
